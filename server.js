require('dotenv').config();
const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const PDFDocument = require('pdfkit');
const helmet = require('helmet');
const morgan = require('morgan');
const { GoogleGenerativeAI } = require('@google/generative-ai');

// Initialize Gemini AI
const geminiApiKey = process.env.GEMINI_API_KEY;
let genAI = null;
let geminiModel = null;
if (geminiApiKey && geminiApiKey !== 'YOUR_GEMINI_API_KEY') {
  genAI = new GoogleGenerativeAI(geminiApiKey);
  const modelName = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  geminiModel = genAI.getGenerativeModel({
    model: modelName,
    generationConfig: {
      temperature: 0.0,
      maxOutputTokens: 1024,
    }
  });
  console.log(`🤖 Gemini AI initialized with model: ${modelName}`);
}

// ─── IN-MEMORY RATE LIMITER ─────────────────────────────────────────────────────
const rateLimitStore = new Map(); // key → { count, resetAt }

function rateLimiter({ windowMs = 60000, max = 10, keyPrefix = 'rl' } = {}) {
  return (req, res, next) => {
    const ip = req.ip || req.connection.remoteAddress || 'unknown';
    const key = `${keyPrefix}:${ip}`;
    const now = Date.now();

    let entry = rateLimitStore.get(key);
    if (!entry || now > entry.resetAt) {
      entry = { count: 1, resetAt: now + windowMs };
      rateLimitStore.set(key, entry);
      // Cleanup old entries periodically (every ~100 writes)
      if (rateLimitStore.size > 1000) {
        const threshold = Date.now();
        for (const [k, v] of rateLimitStore) {
          if (threshold > v.resetAt) rateLimitStore.delete(k);
        }
      }
      return next();
    }

    entry.count++;
    if (entry.count > max) {
      const retryAfter = Math.ceil((entry.resetAt - now) / 1000);
      res.set('Retry-After', String(retryAfter));
      return res.status(429).json({ success: false, message: 'Too many requests. Please wait and try again.' });
    }

    return next();
  };
}

// Escape HTML to prevent XSS in web viewers
function escapeHtml(str) {
  if (!str) return "";
  return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

const app = express();
app.use(express.json({ limit: '1mb' }));
app.set('trust proxy', 1);

// CORS — always allow the Vercel frontend + any extra origins in ALLOWED_ORIGIN env var
const HARDCODED_ORIGINS = [
  'https://grahbook.vercel.app',
  'https://grahbook.in',
  'http://localhost:3000',
  'http://localhost:3001',
];
const extraOrigins = (process.env.ALLOWED_ORIGIN || '').split(',').map(o => o.trim()).filter(Boolean);
const corsAllowedOrigins = [...new Set([...HARDCODED_ORIGINS, ...extraOrigins])];

app.use((req, res, next) => {
  const origin = req.get('Origin');
  if (!origin) {
    // Same-origin or server-to-server — always allow
    res.setHeader('Access-Control-Allow-Origin', '*');
  } else if (corsAllowedOrigins.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
  }
  // null origin is denied (sandboxed iframes — CSRF protection)
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, PATCH, DELETE');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-API-KEY, X-ADMIN-KEY');
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Max-Age', '86400');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

// Security headers
app.use(helmet({
  contentSecurityPolicy: false, // Disable CSP for now — requires careful tuning for inline scripts
  crossOriginEmbedderPolicy: false, // Allow embedding PDFs
}));

// Request logging
app.use(morgan('short'));

const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'db.json');

// ─── MOBILE API KEY AUTH ──────────────────────────────────────────────────────

function requiresMobileApiKey(req) {
  if (!req.path.startsWith('/api/')) return false;

  // Auth + registration endpoints should work without the mobile API key.
  if (req.path === '/api/auth/request-code') return false;
  if (req.path === '/api/auth/verify-code') return false;
  if (req.path === '/api/auth/login') return false;
  if (req.path === '/api/auth/google') return false;
  if (req.path === '/api/register-store') return false;
  if (req.path.startsWith('/api/store/')) return false;
  // Allow PDF endpoints to be fetched by WhatsApp/clients without an app key.
  if (req.method === 'GET') {
    if (req.path === '/api/test-db') return false;
    if (req.path === '/api/test-wa') return false;
    if (req.path === '/api/app/version') return false;
    if (req.path === '/api/debug/store-status') return false;
    if (/^\/api\/bill\/[^/]+\/pdf$/.test(req.path)) return false;
    if (/^\/api\/customer\/[^/]+\/statement\/pdf$/.test(req.path)) return false;
    if (/^\/api\/report\/[^/]+\/pdf$/.test(req.path)) return false;
    if (req.path === '/api/admin/debug-user') return false;
  }

  // WhatsApp webhook is not under /api.
  return true;
}

function mobileApiKeyMiddleware(req, res, next) {
  if (!requiresMobileApiKey(req)) return next();

  // If the request has a Bearer token (web dashboard session), let sessionAuthMiddleware handle it
  if (req.get('Authorization')?.startsWith('Bearer ')) return next();

  const expectedKey = process.env.MOBILE_API_KEY;
  if (!expectedKey) {
    return res.status(500).json({ success: false, message: 'Server misconfigured: MOBILE_API_KEY missing' });
  }

  const providedKey = req.get('X-API-KEY');
  if (!providedKey) {
    return res.status(401).json({ success: false, message: 'Unauthorized (Missing API Key or Session Token)' });
  }

  // Timing-safe comparison to prevent timing attacks
  try {
    const expectedBuf = Buffer.from(expectedKey, 'utf8');
    const providedBuf = Buffer.from(providedKey, 'utf8');
    if (expectedBuf.length !== providedBuf.length || !crypto.timingSafeEqual(expectedBuf, providedBuf)) {
      return res.status(401).json({ success: false, message: 'Unauthorized' });
    }
  } catch {
    return res.status(401).json({ success: false, message: 'Unauthorized' });
  }

  return next();
}

app.use(mobileApiKeyMiddleware);

// ─── STORE SESSION AUTH (Bearer token) ────────────────────────────────────────

function requiresSessionAuth(req) {
  if (!req.path.startsWith('/api/')) return false;

  // Auth + registration endpoints must be reachable without a session.
  if (req.path === '/api/register-store') return false;
  if (req.path === '/api/store/') return false;
  // /api/store/activate and /api/store/update require session auth
  if (req.path.startsWith('/api/store/') && req.path !== '/api/store/activate' && req.path !== '/api/store/update') return false;
  if (req.path === '/api/auth/request-code') return false;
  if (req.path === '/api/auth/verify-code') return false;
  if (req.path === '/api/auth/login') return false;
  if (req.path === '/api/auth/google') return false;
  if (req.path === '/api/auth/logout') return false;
  if (req.path === '/api/admin/clear-all') return false;
  if (req.path === '/api/admin/wipe-merchants') return false;
  if (req.path === '/api/admin/find-dup-stores') return false;

  // Test/version/debug endpoints
  if (req.path === '/api/test-db') return false;
  if (req.path === '/api/debug/store-status') return false;
  if (req.path === '/api/test-wa') return false;
  if (req.path === '/api/app/version') return false;
  if (req.path === '/api/admin/debug-user') return false;

  // PDF endpoints now require session auth (Bearer token).
  // This secures customer PII while still allowing access from the app and web admin.

  return true;
}

async function sessionAuthMiddleware(req, res, next) {
  if (!requiresSessionAuth(req)) return next();

  const auth = req.get('Authorization') || '';
  const token = auth.startsWith('Bearer ') ? auth.slice('Bearer '.length).trim() : '';
  if (!token) return res.status(401).json({ success: false, message: 'Missing session token' });

  try {
    const database = await connectDB();
    if (!database) return res.status(500).json({ success: false, message: 'Server DB not configured' });

    const session = await database.collection('sessions').findOne({ token, expires_at: { $gt: new Date() } });
    if (!session) return res.status(401).json({ success: false, message: 'Invalid/expired session' });

    req.storeId = session.store_id;
    req.staffPhone = session.phone;
    return next();
  } catch (e) {
    console.error('Session auth error:', e);
    return res.status(500).json({ success: false, message: 'Auth failed' });
  }
}

app.use(sessionAuthMiddleware);

// ─── SIGNED URL TOKENS FOR PDF SHARING ────────────────────────────────────────
// Generates time-limited tokens so PDFs can be shared via WhatsApp without auth.
const PDF_TOKEN_SECRET = process.env.PDF_TOKEN_SECRET || crypto.randomBytes(32).toString('hex');

function generatePdfToken(resourceType, resourceId, storeId) {
  const payload = `${resourceType}:${resourceId}:${storeId}:${Date.now()}`;
  const signature = crypto.createHmac('sha256', PDF_TOKEN_SECRET).update(payload).digest('hex');
  return Buffer.from(`${payload}:${signature}`).toString('base64url');
}

function verifyPdfToken(token, resourceType, resourceId) {
  try {
    const decoded = Buffer.from(token, 'base64url').toString('utf8');
    const parts = decoded.split(':');
    if (parts.length !== 5) return null;
    const [type, id, storeId, timestamp, signature] = parts;
    if (type !== resourceType || id !== resourceId) return null;

    // Token expires after 24 hours
    if (Date.now() - parseInt(timestamp) > 24 * 60 * 60 * 1000) return null;

    const expectedSig = crypto.createHmac('sha256', PDF_TOKEN_SECRET)
      .update(`${type}:${id}:${storeId}:${timestamp}`).digest('hex');
    if (signature !== expectedSig) return null;

    return { storeId };
  } catch {
    return null;
  }
}

// Middleware: allow PDF access via valid signed token OR session auth
function pdfAuthMiddleware(req, res, next) {
  // If session auth already passed, allow
  if (req.storeId) return next();

  // Check for signed token in query string
  const token = req.query.token;
  if (!token) {
    return res.status(401).json({ success: false, message: 'Authentication required' });
  }

  // Determine resource type from path
  const billMatch = req.path.match(/^\/api\/bill\/([^/]+)\/pdf$/);
  const stmtMatch = req.path.match(/^\/api\/customer\/([^/]+)\/statement\/pdf$/);
  const reportMatch = req.path.match(/^\/api\/report\/([^/]+)\/pdf$/);

  let resourceType, resourceId;
  if (billMatch) { resourceType = 'bill'; resourceId = billMatch[1]; }
  else if (stmtMatch) { resourceType = 'statement'; resourceId = stmtMatch[1]; }
  else if (reportMatch) { resourceType = 'report'; resourceId = reportMatch[1]; }
  else { return res.status(401).json({ success: false, message: 'Invalid resource' }); }

  const verified = verifyPdfToken(token, resourceType, resourceId);
  if (!verified) {
    return res.status(401).json({ success: false, message: 'Invalid or expired link' });
  }

  req.storeId = verified.storeId;
  return next();
}

function getPublicBaseUrl(req) {
  return process.env.PUBLIC_BASE_URL || `${req.protocol}://${req.get('host')}`;
}

function normalizePhone(phone) {
  if (!phone) return '';
  let p = String(phone).trim();
  // Strip all non-digit characters
  p = p.replace(/[^\d]/g, '');
  // If 10 digits, assume Indian number and prepend country code 91
  if (p.length === 10) {
    p = '91' + p;
  }
  // Strip leading zeros
  p = p.replace(/^0+/, '');
  return p;
}

async function readStoreDB(storeId) {
  const db = await readDB();
  const sid = storeId || 'default';

  const store = (db.stores || []).find(s => s.id === sid);
  const shop =
    store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          store_id: sid,
          upi_id: store.upi_id || '',
          gstin: store.gstin || '',
          invoice_template: store.invoice_template || 'modern',
        }
      : (sid === 'default' && db.shop ? { ...db.shop, upi_id: db.shop.upi_id || '', gstin: db.shop.gstin || '', invoice_template: db.shop.invoice_template || 'modern' } : {});

  const OVERDUE_DAYS = 30;
  const now = new Date();
  const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === sid).map(b => {
    if (b.status === 'unpaid' && b.created_at) {
      const billDate = new Date(b.created_at);
      const daysDiff = (now - billDate) / (1000 * 60 * 60 * 24);
      if (daysDiff > OVERDUE_DAYS) {
        // Return a new object — do NOT mutate the cached original
        return { ...b, status: 'overdue' };
      }
    }
    return b;
  });

  return {
    shop,
    customers: (db.customers || []).filter(c => (c.store_id || 'default') === sid),
    transactions: (db.transactions || []).filter(t => (t.store_id || 'default') === sid),
    bills: storeBills,
    staff: (db.staff || []).filter(s => (s.store_id || 'default') === sid),
    items: (db.items || []).filter(i => (i.store_id || 'default') === sid),
  };
}

// ─── DATABASE HELPERS (MONGODB) ────────────────────────────────────────────────
const { connectDB, getFullDB } = require('./db.js');

let cachedDB = { shop: {}, customers: [], transactions: [], bills: [], staff: [], stores: [] };
let dbCacheTimestamp = 0;
const DB_CACHE_TTL = 5000; // 5 seconds cache TTL

// Session memory for language preference and conversation context
const sessionMemory = new Map(); // staffPhone -> { language: 'hindi'|'english', history: [], lastActivity: timestamp }
const SESSION_TTL = 30 * 60 * 1000; // 30 minutes session TTL

// Helper functions for session management
function getSession(staffPhone) {
  const now = Date.now();
  let session = sessionMemory.get(staffPhone);
  
  // Clean up expired sessions
  if (session && (now - session.lastActivity) > SESSION_TTL) {
    sessionMemory.delete(staffPhone);
    session = null;
  }
  
  // Create new session if doesn't exist
  if (!session) {
    session = {
      language: 'hindi', // Default to Hindi
      history: [],
      lastActivity: now
    };
    sessionMemory.set(staffPhone, session);
  }
  
  session.lastActivity = now;
  return session;
}

function setLanguage(staffPhone, language) {
  const session = getSession(staffPhone);
  session.language = language.toLowerCase();
  return session.language;
}

function getLanguage(staffPhone) {
  const session = getSession(staffPhone);
  return session.language;
}

function addToHistory(staffPhone, message, response) {
  const session = getSession(staffPhone);
  session.history.push({ message, response, timestamp: Date.now() });
  
  // Keep only last 10 messages to avoid memory bloat
  if (session.history.length > 10) {
    session.history = session.history.slice(-10);
  }
}

function extractCustomerId(message) {
  // Extract customer ID from message in various formats
  const patterns = [
    /\(?\s*ID:\s*(c_[a-z0-9]+)\s*\)?/i,
    /customer\s+ID:\s*(c_[a-z0-9]+)/i,
    /customer\s+(c_[a-z0-9]+)/i,
    /\b(c_[a-z0-9]{8,})\b/
  ];
  
  for (const pattern of patterns) {
    const match = message.match(pattern);
    if (match) {
      return match[1];
    }
  }
  return null;
}

function getConversationContext(staffPhone) {
  const session = getSession(staffPhone);
  return session.history.slice(-5); // Return last 5 messages for context
}

async function readDB() {
  const now = Date.now();
  if (cachedDB && (now - dbCacheTimestamp) < DB_CACHE_TTL) {
    return cachedDB;
  }
  try {
    cachedDB = await getFullDB();
    dbCacheTimestamp = now;
    return cachedDB;
  } catch (error) {
    console.error('Error reading from MongoDB:', error);
    return cachedDB;
  }
}

// Write lock to prevent concurrent DB corruption
let dbWriteLock = false;
let lastDbVersion = 0;

async function writeDB(data, expectedVersion) {
  // Optimistic locking: reject if version mismatch (concurrent write hazard)
  if (expectedVersion !== undefined && expectedVersion !== lastDbVersion) {
    const err = new Error('Conflict: database was modified by another write. Please retry.');
    err.code = 'CONFLICT';
    err.expectedVersion = expectedVersion;
    err.currentVersion = lastDbVersion;
    throw err;
  }

  if (dbWriteLock) {
    let retries = 0;
    while (dbWriteLock && retries < 10) {
      await new Promise(r => setTimeout(r, 100));
      retries++;
    }
    if (dbWriteLock) throw new Error("Write lock timeout after 10 retries");
  }
  dbWriteLock = true;
  try {
    const database = await connectDB();
    if (!database) {
      fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf8');
      cachedDB = data;
      lastDbVersion++;
      return;
    }

    // Atomic bulkWrite: individual upserts → no mass data loss on crash
    // Shop is a single doc → replaceOne with upsert
    if (data.shop) {
      await database.collection('shop').updateOne(
        { _id: 'shop_config' },
        { $set: { ...data.shop, _id: 'shop_config' } },
        { upsert: true }
      );
    }

    // For array collections: upsert each doc AND remove docs not in new data
    for (const col of ['customers', 'transactions', 'bills', 'staff', 'stores', 'items']) {
      const docs = Array.isArray(data[col]) ? data[col] : [];
      const ops = [];

      // Upsert all current docs
      for (const doc of docs) {
        ops.push({
          replaceOne: {
            filter: { id: doc.id || doc._id },
            replacement: doc,
            upsert: true
          }
        });
      }

      // Delete docs that exist in DB but not in new data (handles deletions)
      const currentIds = docs.map(d => d.id).filter(Boolean);
      if (currentIds.length > 0) {
        ops.push({
          deleteMany: {
            filter: { id: { $nin: currentIds }, store_id: data.shop?.store_id || 'default' }
          }
        });
      }

      if (ops.length > 0) {
        await database.collection(col).bulkWrite(ops);
      }
    }
    cachedDB = data;
    lastDbVersion++;
  } catch (error) {
    console.error('Error writing to MongoDB:', error);
    throw error; // Propagate errors — callers must handle failures
  } finally {
    dbWriteLock = false;
  }
}

// POST /api/admin/clear-all — Wipe all stores, customers, staff, bills, transactions
app.post('/api/admin/clear-all', async (req, res) => {
  try {
    // Require X-ADMIN-KEY header
    const adminKey = process.env.ADMIN_KEY;
    if (!adminKey) {
      return res.status(500).json({ success: false, message: 'Server misconfigured: ADMIN_KEY not set' });
    }
    const providedKey = req.get('X-ADMIN-KEY');
    // Timing-safe comparison to prevent timing attacks
    let keyValid = false;
    try {
      const expectedBuf = Buffer.from(adminKey, 'utf8');
      const providedBuf = Buffer.from(providedKey || '', 'utf8');
      keyValid = expectedBuf.length === providedBuf.length && crypto.timingSafeEqual(expectedBuf, providedBuf);
    } catch {
      keyValid = false;
    }
    if (!keyValid) {
      // Log the attempt but don't reveal if the key is correct
      console.warn(`⚠️ Unauthorized /api/admin/clear-all attempt from ${req.ip}`);
      return res.status(403).json({ success: false, message: 'Forbidden' });
    }

    // Require explicit confirmation in request body
    const { confirm, storeId } = req.body || {};
    if (confirm !== true || !storeId) {
      return res.status(400).json({
        success: false,
        message: 'Confirmation required. Send { confirm: true, storeId: "<your-store-id>" } in the body.'
      });
    }

    const empty = { shop: {}, customers: [], transactions: [], bills: [], staff: [], stores: [] };
    await writeDB(empty);
    cachedDB = empty;
    const timestamp = new Date().toISOString();
    console.warn(`🗑️ ALL DATA CLEARED at ${timestamp} by ${req.ip} for store ${storeId}`);
    return res.json({ success: true, message: 'All data cleared. Everyone starts fresh.', timestamp });
  } catch (error) {
    console.error('Error clearing data:', error);
    return res.status(500).json({ success: false, message: 'Failed to clear data' });
  }
});

// ─── UTILITY HELPERS ───────────────────────────────────────────────────────────

// Format date as DD/MM/YYYY (Indian standard)
function fmtDate(isoStr) {
  return new Date(isoStr).toLocaleDateString('en-GB');
}

// Format rupee amount
function fmtRs(amount) {
  return `₹${Number(amount).toLocaleString('en-IN')}`;
}

// Generate a short random ID using crypto
function genId(prefix) {
  return prefix + '_' + crypto.randomBytes(6).toString('hex');
}

// Calculate customer outstanding balance
function getCustomerOutstanding(customerId, transactions, bills) {
  let balance = 0;
  transactions.forEach(t => {
    if (t.customer_id === customerId) {
      if (t.type === 'credit') balance += t.amount;
      else if (t.type === 'payment') balance -= t.amount;
    }
  });
  bills.forEach(b => {
    if (b.customer_id === customerId && b.status === 'unpaid') balance += b.total;
  });
  return balance;
}

// Find customer by name (supports Hindi/English first name or full name)
function findCustomer(text, customers) {
  const sortedCustomers = [...customers].sort((a, b) => b.name.length - a.name.length);
  // Full name match first
  for (const c of sortedCustomers) {
    if (text.includes(c.name.toLowerCase())) return c;
  }
  // First name match fallback
  for (const c of sortedCustomers) {
    const firstName = c.name.split(' ')[0].toLowerCase();
    if (firstName.length > 2 && text.includes(firstName)) return c;
  }
  return null;
}

// ─── DATABASE OPERATION TOOLS FOR GEMINI AI ───────────────────────────────────

async function addCustomerTool(name, phone, storeId) {
  const np = normalizePhone(phone);
  const storeDb = await readStoreDB(storeId);
  const existing = storeDb.customers.find(c => normalizePhone(c.phone) === np || c.name.toLowerCase() === name.toLowerCase());
  if (existing) {
    return { error: `Customer '${name}' or phone '${phone}' is already registered.` };
  }
  const newCustomer = {
    id: genId('c'),
    name: name.replace(/\b\w/g, c => c.toUpperCase()),
    phone: np,
    store_id: storeId || 'default',
    created_at: new Date().toISOString().substring(0, 10)
  };
  const db = await readDB();
  db.customers.push(newCustomer);
  await writeDB(db);
  cachedDB = null; dbCacheTimestamp = 0;
  return { success: true, customer: newCustomer };
}

async function recordPaymentTool(customerId, amount, note, staffPhone, storeId, paymentMode) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
  const newTxId = genId('t');
  db.transactions.push({
    id: newTxId,
    customer_id: customerId,
    type: 'payment',
    amount: Number(amount),
    payment_mode: paymentMode || 'cash',
    note: note || 'Payment recorded via AI Bot',
    staff_phone: staffPhone || 'system',
    timestamp: new Date().toISOString(),
    store_id: storeId || 'default',
  });
  await writeDB(db);
  cachedDB = null; dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, amount: Number(amount), remainingOutstanding: balance };
}

async function addCreditTool(customerId, amount, note, staffPhone, storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
  const newTxId = genId('t');
  db.transactions.push({
    id: newTxId,
    customer_id: customerId,
    type: 'credit',
    amount: Number(amount),
    note: note || 'Credit added via AI Bot',
    staff_phone: staffPhone || 'system',
    timestamp: new Date().toISOString(),
    store_id: storeId || 'default',
  });
  await writeDB(db);
  cachedDB = null; dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, amountAdded: Number(amount), totalOutstanding: balance };
}

async function addItemToUnpaidBillTool(customerId, itemName, price, qty, storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found in your store.` };
  let currentBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
  const timestampIso = new Date().toISOString();
  if (!currentBill) {
    currentBill = {
      id: genId('b'),
      customer_id: customerId,
      items: [],
      total: 0,
      status: 'unpaid',
      store_id: storeId || 'default',
      created_at: timestampIso,
      paid_at: null
    };
    db.bills.push(currentBill);
  }
  const quantity = Number(qty) || 1;
  currentBill.items.push({ name: itemName, qty: quantity, price: Number(price), hsn_code: '', gst_rate: 0, taxable: 0, cgst: 0, sgst: 0, igst: 0, total_with_tax: 0 });
  currentBill.total += Number(price) * quantity;
  await writeDB(db);
  // Invalidate cache to ensure AI gets fresh data
  cachedDB = null;
  dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, itemAdded: itemName, itemPrice: Number(price), qty: quantity, billTotal: currentBill.total, netOutstanding: balance };
}

async function generateBillTool(customerId, amount, storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found in your store.` };
  const newBillId = genId('b');
  const timestampIso = new Date().toISOString();
  db.bills.push({
    id: newBillId,
    customer_id: customerId,
    items: [{ name: 'General Grocery Item', qty: 1, price: Number(amount), hsn_code: '', gst_rate: 0, taxable: 0, cgst: 0, sgst: 0, igst: 0, total_with_tax: 0 }],
    total: Number(amount),
    status: 'unpaid',
    store_id: storeId || 'default',
    created_at: timestampIso,
    paid_at: null
  });
  await writeDB(db);
  // Invalidate cache to ensure AI gets fresh data
  cachedDB = null;
  dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, billId: newBillId, amount: Number(amount), netOutstanding: balance };
}

async function markBillAsPaidTool(customerId, storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found in your store.` };
  const unpaidBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
  if (!unpaidBill) {
    return { error: `No active unpaid bill found for customer '${customer.name}'.` };
  }
  unpaidBill.status = 'paid';
  unpaidBill.paid_at = new Date().toISOString();
  await writeDB(db);
  // Invalidate cache to ensure AI gets fresh data
  cachedDB = null;
  dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, billId: unpaidBill.id, amountPaid: unpaidBill.total, netOutstanding: balance };
}

async function getCustomerBalancesTool(storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const balances = storeCustomers.map(c => {
    const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
    return { id: c.id, name: c.name, phone: c.phone, outstandingBalance: bal };
  });
  return { success: true, balances };
}

async function getBillPdfTool(customerId, storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found in your store.` };

  // Find the latest unpaid bill for this customer
  const unpaidBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
  if (!unpaidBill) {
    return { error: `No unpaid bill found for customer '${(customer.name || customerId)}'. Please create a bill first.` };
  }

  return {
    success: true,
    billId: unpaidBill.id,
    pdfUrl: `/api/bill/${unpaidBill.id}/pdf`,
    customerPhone: customer.phone,
    message: 'PDF generated for existing unpaid bill'
  };
}

async function getCustomerStatementPdfTool(customerId, storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const customer = storeCustomers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found in your store.` };

  const customerTransactions = db.transactions.filter(t => t.customer_id === customerId);
  const customerBills = db.bills.filter(b => b.customer_id === customerId);
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);

  return {
    success: true,
    customerId: customerId,
    customerName: customer.name,
    pdfUrl: `/api/customer/${customerId}/statement/pdf`,
    customerPhone: customer.phone,
    balance: balance,
    transactionsCount: customerTransactions.length,
    billsCount: customerBills.length,
    message: 'Customer statement PDF generated'
  };
}

async function getDailyReportPdfTool(date, storeId) {
  const db = await readDB();
  const targetDate = date || new Date().toISOString().substring(0, 10);

  const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === (storeId || 'default'));
  const billsToday = storeBills.filter(b => b.created_at.startsWith(targetDate));
  const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
  const storeTransactions = (db.transactions || []).filter(t => (t.store_id || 'default') === (storeId || 'default'));
  const collectionsToday = storeTransactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(targetDate));
  const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);
  const creditsToday = storeTransactions.filter(t => t.type === 'credit' && t.timestamp.startsWith(targetDate));
  const creditTotal = creditsToday.reduce((sum, t) => sum + t.amount, 0);

  return {
    success: true,
    date: targetDate,
    pdfUrl: `/api/report/${targetDate}/pdf`,
    sales: billsTotal,
    collections: paymentTotal,
    credits: creditTotal,
    billsCount: billsToday.length,
    message: 'Daily report PDF generated'
  };
}

async function getTodaySalesReportTool(storeId) {
  const db = await readDB();
  const todayString = new Date().toISOString().substring(0, 10);
  const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === (storeId || 'default'));
  const billsToday = storeBills.filter(b => b.created_at.startsWith(todayString));
  const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
  const storeTransactions = (db.transactions || []).filter(t => (t.store_id || 'default') === (storeId || 'default'));
  const collectionsToday = storeTransactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(todayString));
  const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);
  const unpaidCount = billsToday.filter(b => b.status === 'unpaid').length;
  return {
    success: true,
    date: todayString,
    todaySales: billsTotal,
    todayCollections: paymentTotal,
    billsCreated: billsToday.length,
    unpaidBillsCount: unpaidCount
  };
}

async function getShopDetailsTool(storeId) {
  const storeDb = await readStoreDB(storeId);
  return { success: true, shop: storeDb.shop || {} };
}

async function getCustomersListTool(storeId) {
  const db = await readDB();
  const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === (storeId || 'default'));
  const list = storeCustomers.map(c => ({ id: c.id, name: c.name, phone: c.phone }));
  return { success: true, customers: list };
}

// ─── COMMAND PARSER ────────────────────────────────────────────────────────────

function parseCommand(message, customers, transactions, bills) {
  const text = message.toLowerCase().trim();

  // ── HELP ──────────────────────────────────────────────────────────────────────
  if (text === 'help' || text === 'menu' || text === '?' || text.includes('kya kya kar sakte') || text.includes('commands')) {
    return { type: 'help' };
  }

  // ── LANGUAGE SWITCHING ─────────────────────────────────────────────────────────
  if (text.includes('language') || text.includes('bhasha') || text.includes('lang')) {
    if (text.includes('english') || text.includes('angrezi')) {
      return { type: 'set_language', language: 'english' };
    }
    if (text.includes('hindi') || text.includes('hinglish')) {
      return { type: 'set_language', language: 'hindi' };
    }
  }

  // ── TODAY'S SALE REPORT ───────────────────────────────────────────────────────
  if (
    (text.includes('aaj') && (text.includes('sale') || text.includes('bikri') || text.includes('collect') || text.includes('kamai'))) ||
    text.includes('today sale') || text.includes("today's sale") || text.includes('daily report') || text.includes('aaj ki report')
  ) {
    return { type: 'today_sale' };
  }

  // ── DAILY REPORT PDF ─────────────────────────────────────────────────────────
  if (text.includes('report pdf') || text.includes('daily pdf') || text.includes('aaj ka pdf')) {
    return { type: 'daily_report_pdf' };
  }

  // ── ALL CUSTOMERS OUTSTANDING ─────────────────────────────────────────────────
  if (
    text.includes('sab ka hisab') || text.includes('sabka hisab') ||
    text.includes('all balance') || text.includes('all outstanding') ||
    text.includes('sab ka baaki') || text.includes('list karo') ||
    text.includes('kitne baaki hain') || text.includes('poori list')
  ) {
    return { type: 'all_outstanding' };
  }

  // ── SEND PAYMENT REMINDERS ────────────────────────────────────────────────────
  if (
    text.includes('reminder bhejo') || text.includes('reminder send') ||
    text.includes('sab ko reminder') || text.includes('payment reminder') ||
    text.includes('baaki walo ko message')
  ) {
    return { type: 'send_reminders' };
  }

  // ── ADD NEW CUSTOMER ──────────────────────────────────────────────────────────
  // e.g. "naya customer Rahul 9876543210" / "add customer Priya 98765" / "register customer Ramesh 9876543210"
  const newCustMatch = text.match(/(?:naya|new|add|register)\s+(?:customer|khata|grahak|gahak|member)\s+([a-zA-Z\u0900-\u097F\s]+?)\s+(\d{10})/i) ||
    text.match(/([a-zA-Z\u0900-\u097F\s]+?)\s+(?:ko|ka|ke)?\s*(?:customer|khata|grahak)\s+(?:add|register|banaye)\s*(?:karo|kar)?\s*(\d{10})/i);
  if (newCustMatch) {
    const name = newCustMatch[1].trim();
    const phone = newCustMatch[2].trim();
    if (name && phone) {
      return { type: 'add_customer', name: name.replace(/\b\w/g, c => c.toUpperCase()), phone };
    }
  }

  // ── LOOK UP CUSTOMER FOR REMAINING COMMANDS ───────────────────────────────────
  // Check for explicit customer ID format: (ID: c_xxxxx) or ID: c_xxxxx
  const explicitIdMatch = text.match(/\(?\s*ID:\s*(c_[a-z0-9]+)\s*\)?/i);
  if (explicitIdMatch) {
    const customer = customers.find(c => c.id === explicitIdMatch[1]);
    if (customer) {
      const custName = customer.name;
      const custId = customer.id;
      
      // Now check what action they want with this customer
      if (text.includes('bill pdf') || text.includes('pdf bill') || text.includes('invoice pdf')) {
        return { type: 'send_bill_pdf', customerId: custId, customerName: custName };
      }
      if (text.includes('statement pdf') || text.includes('hisab pdf') || text.includes('statement bhej')) {
        return { type: 'send_statement_pdf', customerId: custId, customerName: custName };
      }
      if (text.includes('statement') || text.includes('hisab') || text.includes('ledger')) {
        return { type: 'query_balance', customerId: custId, customerName: custName };
      }
      if (text.includes('bill') || text.includes('invoice')) {
        return { type: 'send_bill', customerId: custId, customerName: custName };
      }
      if (text.includes('kitna baaki') || text.includes('outstanding') || text.includes('balance')) {
        return { type: 'query_balance', customerId: custId, customerName: custName };
      }
      // Payment/credit words with amount
      const payMatch = text.match(/(\d+)\s*(?:diya|diye|payment|jama|liye|mila)/i);
      if (payMatch) {
        return { type: 'record_payment', customerId: custId, customerName: custName, amount: parseInt(payMatch[1]) };
      }
      // Add credit: daal/add/jama/credit/udhaar with amount
      const creditMatchExplicit = text.match(/(\d+)\s*(?:daal|add|jama|credit|udhaar)/i);
      if (creditMatchExplicit) {
        return { type: 'add_credit', customerId: custId, customerName: custName, amount: parseInt(creditMatchExplicit[1]) };
      }
      // Bill banao/bill create with amount
      const billCreateMatch = text.match(/(?:bill|invoice)\s+(?:banao|create|banaiye)\s*(?:ka|ke)?\s*(\d+)/i) ||
        text.match(/(\d+)\s*(?:ka|ke)?\s*(?:bill|invoice)\s+(?:banao|create)/i);
      if (billCreateMatch) {
        return { type: 'generate_bill', customerId: custId, customerName: custName, amount: parseInt(billCreateMatch[1]) };
      }
      // Mark paid
      if (text.includes('mark paid') || text.includes('paid kar') || text.includes('chukta')) {
        return { type: 'mark_paid', customerId: custId, customerName: custName };
      }
      // If just ID provided, return unknown so AI can handle it
      return { type: 'unknown' };
    }
  }

  const customer = findCustomer(text, customers);
  if (!customer) return { type: 'unknown' };

  const custName = customer.name;
  const custId = customer.id;

  // ── SEND BILL ─────────────────────────────────────────────────────────────────
  if (text.includes('bill bhej') || text.includes('send bill') || text.includes('bill send') ||
      text.includes('bill do') || text.includes('bill lao') || text.includes('bill dikhao') ||
      text.includes('bill dekhna') || text.includes('bill chahiye') || text.includes('bill de do')) {
    return { type: 'send_bill', customerId: custId, customerName: custName };
  }

  // ── SEND BILL PDF ─────────────────────────────────────────────────────────────
  if (text.includes('bill pdf') || text.includes('pdf bill') || text.includes('invoice pdf')) {
    return { type: 'send_bill_pdf', customerId: custId, customerName: custName };
  }

  // ── SEND STATEMENT PDF ────────────────────────────────────────────────────────
  if (text.includes('statement pdf') || text.includes('hisab pdf') || text.includes('statement bhej')) {
    return { type: 'send_statement_pdf', customerId: custId, customerName: custName };
  }

  // ── MARK BILL PAID ────────────────────────────────────────────────────────────
  if (
    text.includes('mark paid') || text.includes('paid kar do') ||
    text.includes('chukta') || text.includes('bill paid') || text.includes('paid mark')
  ) {
    return { type: 'mark_paid', customerId: custId, customerName: custName };
  }

  // ── RECORD PAYMENT RECEIVED ───────────────────────────────────────────────────
  // e.g. "Ramesh ne 200 diya" / "Ramesh ne 500 rupaye diye" / "Ramesh payment 300" / "Ramesh se 500 liye"
  const paymentMatch = text.match(/(?:ne|ka|ke|se)?\s*(\d+)\s*(?:diya|diye|rupaye|rs|payment|jama|ada|paid|liye|liya|mila|mil gaye|cash|chuka)/i) ||
    text.match(/payment\s+(\d+)/i) || text.match(/(\d+)\s*(?:diya|diye|ada kiya)/i);
  if (paymentMatch && (text.includes('ne ') || text.includes('payment') || text.includes('diya') || text.includes('diye') || text.includes('ada') || text.includes('liye') || text.includes('mila') || text.includes('cash'))) {
    const amount = parseInt(paymentMatch[1]);
    if (amount > 0) {
      return { type: 'record_payment', customerId: custId, customerName: custName, amount };
    }
  }

  // ── QUERY BALANCE ─────────────────────────────────────────────────────────────
  if (
    text.includes('kitna baaki') || text.includes('outstanding') ||
    text.includes('hisab') || text.includes('balance') ||
    text.includes('baqi') || text.includes('kitna bacha') ||
    text.includes('kya baaki') || text.includes('total due')
  ) {
    return { type: 'query_balance', customerId: custId, customerName: custName };
  }

  // ── ADD ITEM TO BILL ──────────────────────────────────────────────────────────
  // e.g. "Ramesh ka 50 mein Chini add karo" / "Ramesh ka soap 30 add karo"
  const addLineMatch = text.match(
    /(?:(?:ka|ke|mein)\s+(\d+)\s*(?:mein|me|rupee|rupaye|rs|ka)?\s*([a-zA-Z\u0900-\u097F]+?)\s*(?:add|daal|dal|rakh)\s*(?:karo|do)?)|(?:(?:ka|ke)\s*([a-zA-Z\u0900-\u097F]+?)\s+(\d+)\s*(?:add|daal|dal|rakh)\s*(?:karo|do)?)/
  );
  if (addLineMatch) {
    let price = 0; let itemName = '';
    if (addLineMatch[1] && addLineMatch[2]) { price = parseInt(addLineMatch[1]); itemName = addLineMatch[2].trim(); }
    else if (addLineMatch[3] && addLineMatch[4]) { itemName = addLineMatch[3].trim(); price = parseInt(addLineMatch[4]); }
    if (itemName && price > 0) {
      return { type: 'add_item', customerId: custId, customerName: custName, itemName, price };
    }
  }

  // ── GENERATE FIXED BILL ───────────────────────────────────────────────────────
  // e.g. "Suresh ka 500 ka bill banao"
  const billMatch = text.match(/(?:(?:ka|ke)\s+(\d+)\s*(?:ka|ke)?\s*bill\s*(?:banao|banaiye|create))/i) ||
    text.match(/bill\s+(?:banao|create)?\s*(?:of|for)?\s*(\d+)/i);
  if (billMatch) {
    const amount = parseInt(billMatch[1]);
    return { type: 'generate_bill', customerId: custId, customerName: custName, amount };
  }

  // ── GENERATE BILL (NO AMOUNT) ────────────────────────────────────────────────
  // e.g. "Ramesh ka bill banao" (without amount) → fall through to ask via AI
  if (text.includes('bill banao') || text.includes('bill banaiye') || text.includes('bill create')) {
    // No amount found, so return unknown to let AI handle it (ask for amount or create empty)
    return { type: 'unknown' };
  }

  // ── ADD CREDIT (UDHAR) ────────────────────────────────────────────────────────
  // e.g. "Ramesh ka khata mein 100 daal do"
  const creditMatch = text.match(/(?:(?:khata|khate|account|me|mein)?\s*(\d+)\s*(?:daal|add|jama|credit|plus|deposit|udhar|udhaar))/i) ||
    text.match(/(\d+)\s*(?:daal|add|jama|credit|deposit|udhar)/i);
  if (creditMatch) {
    const amount = parseInt(creditMatch[1]);
    if (amount > 0) {
      return { type: 'add_credit', customerId: custId, customerName: custName, amount };
    }
  }

  return { type: 'unknown' };
}

// ─── OPENROUTER AI FUNCTION CALLING (TOOL USE) ────────────────────────────────────

// Map OpenRouter function calls to local JS functions
async function executeTool(name, args, staffPhone, storeId) {
  console.log(`🛠️ Executing AI Tool: "${name}" with args:`, args, `storeId:`, storeId);
  try {
    switch (name) {
      case 'addCustomerTool':
        return await addCustomerTool(args.name, args.phone, storeId);
      case 'recordPaymentTool':
        return await recordPaymentTool(args.customerId, args.amount, args.note, staffPhone, storeId);
      case 'addCreditTool':
        return await addCreditTool(args.customerId, args.amount, args.note, staffPhone, storeId);
      case 'addItemToUnpaidBillTool':
        return await addItemToUnpaidBillTool(args.customerId, args.itemName, args.price, args.qty, storeId);
      case 'generateBillTool':
        return await generateBillTool(args.customerId, args.amount, storeId);
      case 'markBillAsPaidTool':
        return await markBillAsPaidTool(args.customerId, storeId);
      case 'getCustomerBalancesTool':
        return await getCustomerBalancesTool(storeId);
      case 'getTodaySalesReportTool':
        return await getTodaySalesReportTool(storeId);
      case 'getShopDetailsTool':
        return await getShopDetailsTool(storeId);
      case 'getCustomersListTool':
        return await getCustomersListTool(storeId);
      case 'getBillPdfTool':
        return await getBillPdfTool(args.customerId, storeId);
      case 'getCustomerStatementPdfTool':
        return await getCustomerStatementPdfTool(args.customerId, storeId);
      case 'getDailyReportPdfTool':
        return await getDailyReportPdfTool(args.date, storeId);
      default:
        return { error: `Tool "${name}" is not implemented.` };
    }
  } catch (err) {
    console.error(`❌ Error executing tool "${name}":`, err);
    return { error: err.message };
  }
}

async function askGeminiWithTools(messageText, staffPhone, storeId) {
  // Try to initialize Gemini if not done
  if (!geminiModel) {
    const currentApiKey = process.env.GEMINI_API_KEY;
    if (currentApiKey && currentApiKey !== 'YOUR_GEMINI_API_KEY') {
      genAI = new GoogleGenerativeAI(currentApiKey);
      const modelName = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
      geminiModel = genAI.getGenerativeModel({
        model: modelName,
        generationConfig: { temperature: 0.0, maxOutputTokens: 1024 },
      });
    }
  }

  if (!geminiModel) {
    console.warn('⚠️ Gemini AI is not configured. GEMINI_API_KEY is missing.');
    return '⚠️ GEMINI_API_KEY missing. Please configure it in your .env file.';
  }

  const conversationContext = getConversationContext(staffPhone);

  try {
    const storeDb = await readStoreDB(storeId);
    const shopInfo = storeDb.shop || {};

    const customerListString = (storeDb.customers || []).length > 0
      ? (storeDb.customers || []).map(c => `  ID: ${c.id} — ${c.name} (${c.phone})`).join('\n')
      : '  (No customers registered yet.)';

    const todayString = new Date().toISOString().substring(0, 10);
    const todayBills = (storeDb.bills || []).filter(b => b.created_at?.startsWith(todayString));
    const todaySales = todayBills.reduce((s, b) => s + (b.total || 0), 0);
    const todayCollections = (storeDb.transactions || [])
      .filter(t => t.type === 'payment' && t.timestamp?.startsWith(todayString))
      .reduce((s, t) => s + (t.amount || 0), 0);

    const systemInstruction = `Tu ${shopInfo.name || 'store'} ka AI assistant hai. Shop owner: ${shopInfo.owner || 'unknown'}, Address: ${shopInfo.address || 'unknown'}.

Store ka aaj ka data:
📊 Aaj ki sales: ₹${todaySales}
💰 Aaj ka collection: ₹${todayCollections}
👥 Total customers: ${(storeDb.customers || []).length}

TERE PAAS YEH LOG HAIN (INHI ke saath kaam kar, bahar ka koi nahi):
${customerListString}

JO TOOLS TU USE KAR SAKTA HAI:
- addCustomerTool(name, phone) → naya customer add
- addItemToUnpaidBillTool(customerId, itemName, price, qty) → item add kare bill mein
- generateBillTool(customerId, amount) → fixed amount ka bill
- recordPaymentTool(customerId, amount, note) → payment record
- addCreditTool(customerId, amount, note) → udhaar add
- markBillAsPaidTool(customerId) → bill paid mark
- getCustomerBalancesTool() → sab customers ka balance
- getTodaySalesReportTool() → aaj ki sale
- getShopDetailsTool() → shop info
- getCustomersListTool() → customer list
- getBillPdfTool(customerId) → bill ka PDF bhejna
- getCustomerStatementPdfTool(customerId) → statement PDF
- getDailyReportPdfTool(date) → daily report PDF

CRITICAL RULES (inhe todna mana hai):
1. SIRF HINGLISH ya HINDI mein jawab de. English mein kabhi jawab mat dena.
2. JO CUSTOMER UPAR LISTED HAI, UNHI KE SAATH KAAM KAR. Agar list mein nahi to pehle add kar via addCustomerTool.
3. NEVER fake data — agar customer/amount/data nahi hai to "Yeh data abhi available nahi hai" bolo.
4. Customer ID kabhi khud se mat banao — tool se banao ya list mein se lo.
5. Ek turn mein ek se zyada tool call kar sakta hai.
6. Paise tool ko hamesha number mein bhejo (500), "five hundred" nahi.
7. Agar tool error de to wahi error batao — success mat banao.
8. Context yaad rakho — jo pehle ho chuka hai wapas mat poocho.
9. Koi bhi random amount, item, ya transaction mat banao. REAL DATA use karo.`;

    // Build conversation context
    let fullInstruction = systemInstruction;
    let lastCustomerId = null;
    if (conversationContext.length > 0) {
      fullInstruction += '\n\nPichli baatein (yaad rakho):\n';
      conversationContext.forEach((ctx, idx) => {
        fullInstruction += `${idx + 1}. User: "${ctx.message}"\n   Assistant: "${ctx.response.substring(0, 150)}..."\n`;
        const extractedId = extractCustomerId(ctx.message);
        if (extractedId) lastCustomerId = extractedId;
      });
      if (lastCustomerId) {
        fullInstruction += `\n⚠️ User ne pehle customer ID batayi thi: ${lastCustomerId}. Use karo agar wahi customer ho.`;
      }
    }

    // Gemini tool declarations
    const tools = [
      { functionDeclarations: [
        { name: 'addCustomerTool', description: 'Naya customer add karna (name, phone)', parameters: { type: 'OBJECT', properties: { name: { type: 'STRING' }, phone: { type: 'STRING' } }, required: ['name', 'phone'] } },
        { name: 'recordPaymentTool', description: 'Payment record karna (customerId, amount, note)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' }, amount: { type: 'NUMBER' }, note: { type: 'STRING' } }, required: ['customerId', 'amount'] } },
        { name: 'addCreditTool', description: 'Udhaar/credit add karna (customerId, amount, note)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' }, amount: { type: 'NUMBER' }, note: { type: 'STRING' } }, required: ['customerId', 'amount'] } },
        { name: 'addItemToUnpaidBillTool', description: 'Bill mein item add karna (customerId, itemName, price, qty)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' }, itemName: { type: 'STRING' }, price: { type: 'NUMBER' }, qty: { type: 'NUMBER' } }, required: ['customerId', 'itemName', 'price'] } },
        { name: 'generateBillTool', description: 'Fixed amount ka bill banana (customerId, amount)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' }, amount: { type: 'NUMBER' } }, required: ['customerId', 'amount'] } },
        { name: 'markBillAsPaidTool', description: 'Bill paid mark karna (customerId)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' } }, required: ['customerId'] } },
        { name: 'getCustomerBalancesTool', description: 'Sab customers ke outstanding balances dikhana', parameters: { type: 'OBJECT', properties: {} } },
        { name: 'getTodaySalesReportTool', description: 'Aaj ki sales report dikhana', parameters: { type: 'OBJECT', properties: {} } },
        { name: 'getShopDetailsTool', description: 'Shop ka naam, owner, address dikhana', parameters: { type: 'OBJECT', properties: {} } },
        { name: 'getCustomersListTool', description: 'Saare customers ki list dikhana', parameters: { type: 'OBJECT', properties: {} } },
        { name: 'getBillPdfTool', description: 'Bill ka PDF generate karna (customerId)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' } }, required: ['customerId'] } },
        { name: 'getCustomerStatementPdfTool', description: 'Customer statement ka PDF (customerId)', parameters: { type: 'OBJECT', properties: { customerId: { type: 'STRING' } }, required: ['customerId'] } },
        { name: 'getDailyReportPdfTool', description: 'Daily report PDF (date YYYY-MM-DD optional)', parameters: { type: 'OBJECT', properties: { date: { type: 'STRING' } } } }
      ]}
    ];

    // Start chat with system instruction
    const chat = geminiModel.startChat({
      systemInstruction: fullInstruction,
      tools: tools,
    });

    // Send the message — first turn
    const TIMEOUT_MS = 30000;
    const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error("TIMEOUT")), TIMEOUT_MS));
    let result = await Promise.race([chat.sendMessage(messageText), timeoutPromise]);
    let response = result.response;
    let loopCount = 0;
    let pdfUrlToSend = null;
    let pdfFilename = "document.pdf";
    let pdfCustomerPhone = null;

    // Tool-use loop (max 10 rounds so complex flows complete)
    while (loopCount < 10) {
      const functionCalls = response.functionCalls();
      if (!functionCalls || functionCalls.length === 0) break;

      loopCount++;
      const toolResults = [];

      for (const fnCall of functionCalls) {
        const fnName = fnCall.name;
        const fnArgs = fnCall.args;
        console.log(`🛠️ AI calling tool: "${fnName}" with args:`, fnArgs);
        const toolResult = await executeTool(fnName, fnArgs, staffPhone, storeId);
        // Track PDF tool results for document sending
        if (["getBillPdfTool", "getCustomerStatementPdfTool", "getDailyReportPdfTool"].includes(fnName)) {
          if (toolResult && toolResult.pdfUrl) {
            pdfUrlToSend = toolResult.pdfUrl;
            pdfFilename = toolResult.filename || `document-${fnName.replace('Tool', '')}.pdf`;
            if (toolResult.customerPhone) pdfCustomerPhone = toolResult.customerPhone;
          }
        }
        console.log(`   ↳ ${fnName} result:`, JSON.stringify(toolResult).substring(0, 200));
        toolResults.push({
          functionResponse: {
            name: fnName,
            response: { result: toolResult }
          }
        });
      }

      // Send tool results back to model
      result = await Promise.race([chat.sendMessage([{ text: 'Tool results follow:' }, ...toolResults]), timeoutPromise]);
      response = result.response;
    }

    let finalResponse = "✅ Done! What else can I help you with?";
    try {
      const text = response.text();
      if (text && text.trim()) finalResponse = text.trim();
    } catch (e) {
      console.error("Response text extraction error:", e);
      finalResponse = loopCount >= 10
        ? "⏳ Bahut saare kaam ho gaye. Kuch aur batao?"
        : "✅ Kaam ho gaya! Kya aur karna hai?";
    }

    addToHistory(staffPhone, messageText, finalResponse);

    if (pdfUrlToSend) {
      return JSON.stringify({ text: finalResponse, pdfUrl: pdfUrlToSend, pdfFilename: pdfFilename, customerPhone: pdfCustomerPhone, success: true });
    }
    return finalResponse;

  } catch (error) {
    console.error('❌ Gemini AI error:', error.message || error);
    if (error.message === 'TIMEOUT') {
      return '⏳ Request timed out. Please try again.';
    }
    if (error.message?.includes('429') || error.message?.includes('quota') || error.message?.includes('RATE_LIMIT')) {
      return `⏳ बॉट अभी व्यस्त है। एक मिनट बाद दोबारा कोशिश करें। 🙏`;
    }
    if (error.message?.includes('network') || error.message?.includes('ECONNREFUSED')) {
      return `📶 नेटवर्क एरर। कृपया बाद में कोशिश करें।`;
    }
    return `❌ कुछ तकनीकल समस्या हुई। कृपया दोबारा कोशिश करें या एडमिन को बताएं। 🙏`;
  }
}

// ─── WHATSAPP SENDER ───────────────────────────────────────────────────────────

async function sendWhatsAppMessage(recipientPhone, textMessage) {
  const token = process.env.WHATSAPP_TOKEN;
  const phoneId = process.env.PHONE_NUMBER_ID;

  if (!token || !phoneId || token === 'your_whatsapp_access_token_here') {
    console.log(`⚠️  WhatsApp token not configured. Local log only:`);
    console.log(`    To: ${recipientPhone}\n    Msg: ${textMessage}`);
    return false;
  }

  try {
    const response = await axios.post(
      `https://graph.facebook.com/v19.0/${phoneId}/messages`,
      {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: recipientPhone,
        type: 'text',
        text: { body: textMessage }
      },
      { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
    );
    return response.status === 200 || response.status === 201;
  } catch (error) {
    console.error('❌ WhatsApp API error:', error.response ? JSON.stringify(error.response.data) : error.message);
    return false;
  }
}

async function sendWhatsAppDocument(recipientPhone, documentUrl, filename, caption) {
  const token = process.env.WHATSAPP_TOKEN;
  const phoneId = process.env.PHONE_NUMBER_ID;

  if (!token || !phoneId || token === 'your_whatsapp_access_token_here') {
    console.log(`⚠️  WhatsApp token not configured. Local log only:`);
    console.log(`    To: ${recipientPhone}\n    Doc: ${filename}\n    URL: ${documentUrl}`);
    return false;
  }

  try {
    const response = await axios.post(
      `https://graph.facebook.com/v19.0/${phoneId}/messages`,
      {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: recipientPhone,
        type: 'document',
        document: {
          link: documentUrl,
          filename: filename,
          caption: caption || ''
        }
      },
      { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
    );
    return response.status === 200 || response.status === 201;
  } catch (error) {
    console.error('❌ WhatsApp document API error:', error.response ? JSON.stringify(error.response.data) : error.message);
    return false;
  }
}

// ─── MEMORY CLEANUP SCHEDULER ────────────────────────────────────────────
// Clean up expired sessions and rate limit entries every 5 minutes
setInterval(() => {
  const now = Date.now();
  // Clean session memory
  for (const [phone, session] of sessionMemory) {
    if (now - session.lastActivity > SESSION_TTL) {
      sessionMemory.delete(phone);
    }
  }
  // Clean rate limit store
  if (rateLimitStore.size > 100) {
    for (const [key, entry] of rateLimitStore) {
      if (now > entry.resetAt) rateLimitStore.delete(key);
    }
  }
}, 5 * 60 * 1000);

// Additional cleanup: every 10 minutes, purge ALL expired rate limit entries unconditionally
setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of rateLimitStore) {
    if (now > entry.resetAt) rateLimitStore.delete(key);
  }
}, 10 * 60 * 1000);

// ─── DAILY REPORT SCHEDULER (runs every day at 9:00 AM IST) ───────────────────

function scheduleDaily(hour, minuteIST, fn) {
  function msUntilNext() {
    const now = new Date();
    // IST = UTC+5:30
    const istOffsetMs = 5.5 * 60 * 60 * 1000;
    const nowIST = new Date(now.getTime() + istOffsetMs);
    const nextRun = new Date(nowIST);
    nextRun.setHours(hour, minuteIST, 0, 0);
    if (nextRun <= nowIST) nextRun.setDate(nextRun.getDate() + 1);
    return nextRun.getTime() - nowIST.getTime();
  }
  async function tick() {
    try { await fn(); } catch(e) { console.error("Scheduled task failed:", e); }
    setTimeout(tick, msUntilNext());
  }
  setTimeout(tick, msUntilNext());
  console.log(`📅 Daily report scheduled at ${hour}:${String(minuteIST).padStart(2,'0')} IST`);
}

async function sendDailyReport() {
  const db = await readDB();
  const todayString = new Date().toISOString().substring(0, 10);

  // Group staff by store_id
  const staffByStore = {};
  for (const staff of (db.staff || [])) {
    const sid = staff.store_id || 'default';
    if (!staffByStore[sid]) staffByStore[sid] = [];
    staffByStore[sid].push(staff);
  }

  // Iterate over each store to send their specific report
  const storeIdsToReport = Object.keys(staffByStore);

  for (const sid of storeIdsToReport) {
    const store = (db.stores || []).find(s => s.id === sid);
    const shop = store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          store_id: sid,
        }
      : (sid === 'default' && db.shop ? db.shop : {});

    const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === sid);
    const storeTransactions = (db.transactions || []).filter(t => (t.store_id || 'default') === sid);
    const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === sid);

    const billsToday = storeBills.filter(b => b.created_at && b.created_at.startsWith(todayString));
    const billsTotal = billsToday.reduce((sum, b) => sum + (b.total || 0), 0);

    const collectionsToday = storeTransactions.filter(t => t.type === 'payment' && t.timestamp && t.timestamp.startsWith(todayString));
    const paymentTotal = collectionsToday.reduce((sum, t) => sum + (t.amount || 0), 0);

    const totalOutstanding = storeCustomers.reduce((sum, c) => {
      const bal = getCustomerOutstanding(c.id, storeTransactions, storeBills);
      return sum + (bal > 0 ? bal : 0);
    }, 0);

    const report =
      `🌅 *${shop.name || 'General Store'} — Subah ki Report*\n` +
      `📅 ${fmtDate(new Date().toISOString())}\n` +
      `━━━━━━━━━━━━━━━━━━━━\n` +
      `💰 Aaj ki total sales: *${fmtRs(billsTotal)}*\n` +
      `📥 Aaj ka collection: *${fmtRs(paymentTotal)}*\n` +
      `🧾 Bills today: ${billsToday.length} (Paid: ${billsToday.filter(b => b.status === 'paid').length})\n` +
      `━━━━━━━━━━━━━━━━━━━━\n` +
      `📊 Total outstanding (sab customers): *${fmtRs(totalOutstanding)}*\n` +
      `👥 Total customers: ${storeCustomers.length}\n` +
      `━━━━━━━━━━━━━━━━━━━━\n` +
      `_${shop.name || 'General Store'} Bot 🤖_`;

    const staffList = staffByStore[sid] || [];
    for (const staff of staffList) {
      if (staff.phone) {
        await sendWhatsAppMessage(staff.phone, report);
        console.log(`📨 Daily report sent to ${staff.name} (${staff.phone}) for store ${sid}`);
      }
    }
  }
}

// ─── WEBHOOK ROUTES ────────────────────────────────────────────────────────────

// GET /webhook — Meta verification
app.get('/webhook', (req, res) => {
  const verifyToken = process.env.VERIFY_TOKEN;
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (mode && token) {
    if (mode === 'subscribe' && token === verifyToken) {
      console.log('✅ Webhook verified!');
      return res.status(200).send(challenge);
    }
    console.warn('❌ Webhook token mismatch');
    return res.sendStatus(403);
  }
  return res.sendStatus(400);
});

// POST /webhook — Incoming WhatsApp messages
// Rate limit: 30 req/min per IP (webhook is called by Meta servers)
app.post('/webhook', rateLimiter({ windowMs: 60000, max: 30, keyPrefix: 'webhook' }), async (req, res) => {
  // ── VERIFY WEBHOOK SIGNATURE ──────────────────────────────────────────────
  // Ensure the message is actually from Meta, not an attacker
  const appSecret = process.env.WHATSAPP_APP_SECRET;
  const signature = req.get('X-Hub-Signature-256');
  if (appSecret && signature) {
    const rawBody = JSON.stringify(req.body);
    const expectedSig = 'sha256=' + crypto.createHmac('sha256', appSecret).update(rawBody).digest('hex');
    try {
      const sigBuf = Buffer.from(signature, 'utf8');
      const expectedBuf = Buffer.from(expectedSig, 'utf8');
      if (sigBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(sigBuf, expectedBuf)) {
        console.warn('❌ Webhook signature mismatch — rejecting request');
        return res.sendStatus(403);
      }
    } catch {
      return res.sendStatus(403);
    }
  } else if (appSecret && !signature) {
    console.warn('❌ Webhook missing signature header — rejecting request');
    return res.sendStatus(403);
  }
  // If no appSecret configured, skip verification (dev mode)

  const body = req.body;

  if (
    body.object === 'whatsapp_business_account' &&
    body.entry?.[0]?.changes?.[0]?.value?.messages
  ) {
    const messageInfo = body.entry[0].changes[0].value.messages[0];
    const staffPhone = messageInfo.from;
    const bodyText = messageInfo.text?.body || '';

    if (!bodyText) return res.sendStatus(200);

    console.log(`📩 [${staffPhone}]: "${bodyText}"`);

    const fullDb = await readDB();
    const staffRow = (fullDb.staff || []).find(s => s.phone === staffPhone);
    if (!staffRow) {
      await sendWhatsAppMessage(staffPhone, '❌ Not authorized. Please register your store and add this number as staff.');
      return res.status(200).json({ success: true, action: 'unauthorized' });
    }

    const storeId = staffRow.store_id || 'default';
    const db = await readStoreDB(storeId);
    const shop = db.shop || {};
    // fullDb already declared at line 1164 for write operations
    const activeStaff = staffRow;
    const action = parseCommand(bodyText, db.customers, db.transactions, db.bills);
    const timestampIso = new Date().toISOString();
    let replyText = '';

    // ── HELP ────────────────────────────────────────────────────────────────────
    if (action.type === 'help') {
      const currentLang = getLanguage(staffPhone);
      if (currentLang === 'english') {
        replyText =
          `🏪 *${shop.name || 'Store'} Bot — Commands*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `👤 *Customer Commands:*\n` +
          `🔹 _Ramesh ka kitna baaki hai_ — Balance check\n` +
          `🔹 _Ramesh ne 200 diya_ — Record payment\n` +
          `🔹 _Ramesh ka khata mein 100 daal do_ — Add credit\n` +
          `🔹 _Ramesh ka 500 ka bill banao_ — Create bill\n` +
          `🔹 _Ramesh ka soap 30 add karo_ — Add item to bill\n` +
          `🔹 _Ramesh ka bill bhej do_ — Send bill summary\n` +
          `🔹 _Ramesh ka bill pdf_ — Send bill PDF to customer\n` +
          `🔹 _Ramesh ka statement pdf_ — Send account statement PDF\n` +
          `🔹 _Ramesh ka bill mark paid_ — Mark bill as paid\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `📊 *Store Commands:*\n` +
          `🔹 _Aaj ki sale kitni thi_ — Today's sales\n` +
          `🔹 _Sab ka hisab batao_ — All outstanding balances\n` +
          `🔹 _Reminder bhejo_ — Send payment reminders\n` +
          `🔹 _Naya customer Rahul 9876543210_ — Add customer\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `📄 *PDF Features:*\n` +
          `🔹 _Bill PDF_ — Generate and send invoice PDF\n` +
          `🔹 _Statement PDF_ — Generate customer account statement\n` +
          `🔹 _Daily Report PDF_ — Generate daily sales report\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `🌐 *Language: English* — Type "language hindi" to switch\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `_Type *help* anytime to see this menu_`;
      } else {
        replyText =
          `🏪 *${shop.name || 'स्टोर'} बॉट — कमांड्स*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `👤 *ग्राहक कमांड्स:*\n` +
          `🔹 _रमेश का कितना बाकी है_ — बैलेंस चेक\n` +
          `🔹 _रमेश ने 200 दिया_ — पेमेंट रिकॉर्ड करें\n` +
          `🔹 _रमेश का खाते में 100 डाल दो_ — क्रेडिट जोड़ें\n` +
          `🔹 _रमेश का 500 का बिल बनाओ_ — बिल बनाएं\n` +
          `🔹 _रमेश का साबुन 30 ऐड करो_ — आइटम ऐड करें\n` +
          `🔹 _रमेश का बिल भेज दो_ — बिल सारांश भेजें\n` +
          `🔹 _रमेश का बिल पीडीएफ_ — बिल पीडीएफ भेजें\n` +
          `🔹 _रमेश का स्टेटमेंट पीडीएफ_ — खाता स्टेटमेंट भेजें\n` +
          `🔹 _रमेश का बिल मार्क पेड_ — बिल पेड मार्क करें\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `📊 *स्टोर कमांड्स:*\n` +
          `🔹 _आज की सेल कितनी थी_ — आज की बिक्री\n` +
          `🔹 _सब का हिसाब बताओ_ — सभी बाकी बैलेंस\n` +
          `🔹 _रिमाइंडर भेजो_ — पेमेंट रिमाइंडर भेजें\n` +
          `🔹 _नया ग्राहक राहुल 9876543210_ — ग्राहक जोड़ें\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `📄 *पीडीएफ फीचर्स:*\n` +
          `🔹 _बिल पीडीएफ_ — इनवॉइस पीडीएफ बनाएं\n` +
          `🔹 _स्टेटमेंट पीडीएफ_ — खाता स्टेटमेंट बनाएं\n` +
          `🔹 _डेली रिपोर्ट पीडीएफ_ — डेली सेल्स रिपोर्ट बनाएं\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `🌐 *भाषा: हिंदी* — "language english" टाइप करें बदलने के लिए\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `_कभी भी *help* टाइप करें मेनू देखने के लिए_`;
      }

    // ── SET LANGUAGE ─────────────────────────────────────────────────────────────
    } else if (action.type === 'set_language') {
      const { language } = action;
      setLanguage(staffPhone, language);
      if (language === 'english') {
        replyText =
          `🌐 *Language Changed to English*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `✅ Bot will now respond in English.\n` +
          `Type "language hindi" to switch back to Hindi.\n` +
          `━━━━━━━━━━━━━━━━━━━━`;
      } else {
        replyText =
          `🌐 *भाषा हिंदी में बदल दी गई*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `✅ बॉट अब हिंदी में जवाब देगा।\n` +
          `अंग्रेजी में बदलने के लिए "language english" टाइप करें।\n` +
          `━━━━━━━━━━━━━━━━━━━━`;
      }

    // ── TODAY'S SALE ─────────────────────────────────────────────────────────────
    } else if (action.type === 'today_sale') {
      const todayString = new Date().toISOString().substring(0, 10);
      const billsToday = db.bills.filter(b => b.created_at.startsWith(todayString));
      const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
      const collectionsToday = db.transactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(todayString));
      const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);
      const unpaidCount = billsToday.filter(b => b.status === 'unpaid').length;

      replyText =
        `📊 *${shop.name || 'Store'} — Aaj ki Report*\n` +
        `📅 ${fmtDate(timestampIso)}\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `💰 Total sales: *${fmtRs(billsTotal)}*\n` +
        `📥 Collection received: *${fmtRs(paymentTotal)}*\n` +
        `🧾 Bills created: ${billsToday.length}\n` +
        `⏳ Unpaid bills: ${unpaidCount}\n` +
        `💸 Pending collection: *${fmtRs(billsTotal - paymentTotal)}*`;

    // ── ALL OUTSTANDING ──────────────────────────────────────────────────────────
    } else if (action.type === 'all_outstanding') {
      const lines = [];
      let grandTotal = 0;
      for (const c of db.customers) {
        const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
        if (bal > 0) {
          lines.push(`• *${c.name}*: ${fmtRs(bal)}`);
          grandTotal += bal;
        }
      }
      if (lines.length === 0) {
        replyText = `✅ Koi bhi outstanding balance nahi hai! Sab ka hisab saaf hai.`;
      } else {
        replyText =
          `📋 *Outstanding Balances*\n` +
          `📅 ${fmtDate(timestampIso)}\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          lines.join('\n') +
          `\n━━━━━━━━━━━━━━━━━━━━\n` +
          `💰 *Kul Outstanding: ${fmtRs(grandTotal)}*`;
      }

    // ── DAILY REPORT PDF ─────────────────────────────────────────────────────────
    } else if (action.type === 'daily_report_pdf') {
      const todayString = new Date().toISOString().substring(0, 10);
      const baseUrl = getPublicBaseUrl(req);
      const pdfUrl = `${baseUrl}/api/report/${todayString}/pdf?token=${generatePdfToken('report', todayString, storeId)}`;
      
      // Send PDF to all staff members
      let sentCount = 0;
      for (const staff of db.staff) {
        await sendWhatsAppDocument(staff.phone, pdfUrl, `daily-report-${todayString}.pdf`, `📊 Daily Sales Report - ${fmtDate(todayString)}`);
        sentCount++;
      }
      
      replyText =
        `📊 *Daily Report PDF Generated!*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `📅 Date: ${fmtDate(todayString)}\n` +
        `📎 PDF sent to ${sentCount} staff members\n` +
        `🔗 Download: ${pdfUrl}`;

    // ── SEND PAYMENT REMINDERS ───────────────────────────────────────────────────
    } else if (action.type === 'send_reminders') {
      const shop = db.shop || {};
      let sentCount = 0;
      for (const c of db.customers) {
        const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
        if (bal > 0 && c.phone) {
          const reminderMsg =
            `🙏 *${shop.name || 'General Store'}*\n\n` +
            `Namaste *${c.name}* ji,\n\n` +
            `Aapka ${fmtRs(bal)} ka baaki hai hamare yahan.\n` +
            `Kripya jald hi chukta karein.\n\n` +
            `Shukriya 🙏\n_${shop.owner || 'Store Owner'}_`;
          await sendWhatsAppMessage(c.phone, reminderMsg);
          sentCount++;
        }
      }
      replyText = sentCount > 0
        ? `✅ *${sentCount} customers* ko payment reminder bhej diya gaya!\n💰 Reminder tab bheja jata hai jab balance > ₹0 ho.`
        : `ℹ️ Kisi ko reminder nahi bheja — koi outstanding balance nahi hai.`;

    // ── ADD NEW CUSTOMER ─────────────────────────────────────────────────────────
    } else if (action.type === 'add_customer') {
      const { name, phone } = action;
      const np = normalizePhone(phone);
      const existing = db.customers.find(c => normalizePhone(c.phone) === np || c.name.toLowerCase() === name.toLowerCase());
      if (existing) {
        replyText = `⚠️ *${name}* ya phone *${phone}* se ek customer pehle se registered hai.`;
      } else {
        const newCustomer = {
          id: genId('c'),
          name,
          phone: np,
          store_id: storeId || 'default',
          created_at: timestampIso.substring(0, 10)
        };
        fullDb.customers.push(newCustomer);
        await writeDB(fullDb);
        cachedDB = null; dbCacheTimestamp = 0;
        replyText =
          `✅ *Naya Customer Add Ho Gaya!*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `👤 Naam: *${name}*\n` +
          `📱 Phone: ${np}\n` +
          `🆔 ID: ${newCustomer.id}\n` +
          `📅 Registered: ${fmtDate(timestampIso)}\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `_Ab aap iske liye bill ya khata bana sakte hain._`;
      }

    // ── RECORD PAYMENT ───────────────────────────────────────────────────────────
    } else if (action.type === 'record_payment') {
      const { customerId, customerName, amount } = action;
      const newTxId = genId('t');
      fullDb.transactions.push({
        id: newTxId,
        customer_id: customerId,
        type: 'payment',
        amount,
        note: `Payment received via Bot by ${activeStaff.name}`,
        staff_phone: staffPhone,
        timestamp: timestampIso,
        store_id: storeId || 'default',
      });
      await writeDB(fullDb);
      cachedDB = null; dbCacheTimestamp = 0;
      const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
      replyText =
        `💵 *Payment Recorded!*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `👤 Customer: *${customerName}*\n` +
        `✅ Amount paid: *${fmtRs(amount)}*\n` +
        `👤 Staff: ${activeStaff.name}\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        (bal > 0
          ? `💳 Remaining baaki: *${fmtRs(bal)}*`
          : bal === 0
            ? `🎉 *Poora hisab saaf ho gaya!*`
            : `😊 *${fmtRs(Math.abs(bal))} advance balance* hai ${customerName} ka.`);

    // ── ADD CREDIT (UDHAR) ───────────────────────────────────────────────────────
    } else if (action.type === 'add_credit') {
      const { customerId, customerName, amount } = action;
      const newTxId = genId('t');
      fullDb.transactions.push({
        id: newTxId,
        customer_id: customerId,
        type: 'credit',
        amount,
        note: `Credit added via Bot by ${activeStaff.name}`,
        staff_phone: staffPhone,
        timestamp: timestampIso,
        store_id: storeId || 'default',
      });
      await writeDB(fullDb);
      cachedDB = null; dbCacheTimestamp = 0;
      const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
      replyText =
        `✅ *Udhar Add Ho Gaya!*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `👤 Customer: *${customerName}*\n` +
        `➕ Udhar: *${fmtRs(amount)}*\n` +
        `👤 Staff: ${activeStaff.name}\n` +
        `💰 Kul Baaki: *${fmtRs(bal)}*`;

    // ── GENERATE BILL ────────────────────────────────────────────────────────────
    } else if (action.type === 'generate_bill') {
      const { customerId, customerName, amount } = action;
      const newBillId = genId('b');
      fullDb.bills.push({
        id: newBillId,
        customer_id: customerId,
        items: [{ name: 'General Grocery Item', qty: 1, price: amount }],
        total: amount,
        status: 'unpaid',
        store_id: storeId || 'default',
        created_at: timestampIso,
        paid_at: null
      });
      await writeDB(fullDb);
      cachedDB = null; dbCacheTimestamp = 0;
      const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
      replyText =
        `📝 *Bill Bana Diya!*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `👤 Customer: *${customerName}*\n` +
        `🧾 Bill #${newBillId}\n` +
        `💵 Amount: *${fmtRs(amount)}*\n` +
        `📌 Status: UNPAID\n` +
        `💳 Total Due: *${fmtRs(bal)}*`;

    // ── ADD ITEM TO BILL ─────────────────────────────────────────────────────────
    } else if (action.type === 'add_item') {
      const { customerId, customerName, itemName, price } = action;
      let currentBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
      if (!currentBill) {
        currentBill = {
          id: genId('b'),
          customer_id: customerId,
          items: [],
          total: 0,
          status: 'unpaid',
          store_id: storeId || 'default',
          created_at: timestampIso,
          paid_at: null
        };
        fullDb.bills.push(currentBill);
      }
      currentBill.items.push({ name: itemName, qty: 1, price, hsn_code: '', gst_rate: 0, taxable: 0, cgst: 0, sgst: 0, igst: 0, total_with_tax: 0 });
      currentBill.total += price;
      await writeDB(fullDb);
      cachedDB = null; dbCacheTimestamp = 0;
      const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
      replyText =
        `🛒 *Item Add Ho Gaya!*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `👤 Customer: *${customerName}*\n` +
        `📦 Item: *${itemName}* — ${fmtRs(price)}\n` +
        `📈 Bill Total: *${fmtRs(currentBill.total)}*\n` +
        `💳 Net Outstanding: *${fmtRs(bal)}*`;

    // ── QUERY BALANCE ────────────────────────────────────────────────────────────
    } else if (action.type === 'query_balance') {
      const { customerId, customerName } = action;
      const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
      const unpaidBills = db.bills.filter(b => b.customer_id === customerId && b.status === 'unpaid');
      replyText =
        `ℹ️ *${customerName} ka Hisab*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        (bal > 0
          ? `💳 Baaki: *${fmtRs(bal)}*\n🧾 Unpaid bills: ${unpaidBills.length}`
          : bal === 0
            ? `✅ Poora chukta hai! Balance ₹0`
            : `😊 Advance balance: *${fmtRs(Math.abs(bal))}*`);

    // ── SEND BILL SUMMARY ────────────────────────────────────────────────────────
    } else if (action.type === 'send_bill') {
      const { customerId, customerName } = action;
      const customerBills = db.bills.filter(b => b.customer_id === customerId);
      if (customerBills.length === 0) {
        replyText = `❌ *${customerName}* ka koi bill nahi mila.`;
      } else {
        const latestBill = customerBills[customerBills.length - 1];
        const itemLines = latestBill.items.map((it, i) => `  ${i + 1}. ${it.name} (${it.qty}x ${fmtRs(it.price)})`).join('\n');
        replyText =
          `📄 *${shop.name || 'GENERAL STORE'}*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `🧾 Bill #${latestBill.id}\n` +
          `📅 Date: ${fmtDate(latestBill.created_at)}\n` +
          `👤 Customer: *${customerName}*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          itemLines + '\n' +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `💰 *Grand Total: ${fmtRs(latestBill.total)}*\n` +
          `📌 Status: *${latestBill.status.toUpperCase()}*`;
      }

    // ── SEND BILL PDF ─────────────────────────────────────────────────────────────
    } else if (action.type === 'send_bill_pdf') {
      const { customerId, customerName } = action;
      const unpaidBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
      if (!unpaidBill) {
        replyText = `❌ *${customerName}* ka koi unpaid bill nahi hai. Pehle bill banayein.`;
      } else {
        const baseUrl = getPublicBaseUrl(req);
        const pdfUrl = `${baseUrl}/api/bill/${unpaidBill.id}/pdf?token=${generatePdfToken('bill', unpaidBill.id, unpaidBill.store_id || storeId)}`;
        const customer = db.customers.find(c => c.id === customerId);
        if (customer && customer.phone) {
          await sendWhatsAppDocument(customer.phone, pdfUrl, `invoice-${unpaidBill.id}.pdf`, `🧾 Your invoice from ${shop.name || 'General Store'}`);
        }
        replyText =
          `📄 *Bill PDF Generated!*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `👤 Customer: *${customerName}*\n` +
          `🧾 Bill #${unpaidBill.id}\n` +
          `💰 Amount: *${fmtRs(unpaidBill.total)}*\n` +
          `📎 PDF sent to customer: +91 ${customer?.phone || 'N/A'}\n` +
          `🔗 Download: ${pdfUrl}`;
      }

    // ── SEND STATEMENT PDF ───────────────────────────────────────────────────────
    } else if (action.type === 'send_statement_pdf') {
      const { customerId, customerName } = action;
      const customer = db.customers.find(c => c.id === customerId);
      const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
      const baseUrl = getPublicBaseUrl(req);
      const pdfUrl = `${baseUrl}/api/customer/${customerId}/statement/pdf?token=${generatePdfToken('statement', customerId, storeId)}`;
      if (customer && customer.phone) {
        await sendWhatsAppDocument(customer.phone, pdfUrl, `statement-${customer.name.replace(/\s+/g, '_')}.pdf`, `📊 Your account statement from ${shop.name || 'General Store'}`);
      }
      replyText =
        `📊 *Statement PDF Generated!*\n` +
        `━━━━━━━━━━━━━━━━━━━━\n` +
        `👤 Customer: *${customerName}*\n` +
        `💳 Current Balance: *${fmtRs(balance)}*\n` +
        `📎 PDF sent to customer: +91 ${customer?.phone || 'N/A'}\n` +
        `🔗 Download: ${pdfUrl}`;

    // ── MARK BILL PAID ───────────────────────────────────────────────────────────
    } else if (action.type === 'mark_paid') {
      const { customerId, customerName } = action;
      const unpaidBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
      if (!unpaidBill) {
        replyText = `ℹ️ *${customerName}* ka koi unpaid bill nahi hai.`;
      } else {
        unpaidBill.status = 'paid';
        unpaidBill.paid_at = timestampIso;
        await writeDB(fullDb);
        cachedDB = null; dbCacheTimestamp = 0;
        const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
        replyText =
          `✅ *Bill Mark Paid Ho Gaya!*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `👤 Customer: *${customerName}*\n` +
          `🧾 Bill #${unpaidBill.id} — ${fmtRs(unpaidBill.total)}\n` +
          `📅 Paid on: ${fmtDate(timestampIso)}\n` +
          (bal > 0
            ? `💳 Remaining baaki: *${fmtRs(bal)}*`
            : `🎉 *Poora hisab saaf ho gaya!*`);
      }
    // ── UNKNOWN → AI AGENT WITH TOOL USE (async — respond immediately) ──────
    } else {
      console.log(`🤖 Regex did not match. AI Agent handling: "${bodyText}"`);
      // Respond to Meta immediately (avoids timeout/retry), process AI in background
      res.status(200).json({ success: true, action: 'ai_processing', aiUsed: true });

      // Per-phone rate limit: max 5 AI calls per minute per phone
      const phoneKey = `ai_phone:${staffPhone}`;
      const now = Date.now();
      let phoneEntry = rateLimitStore.get(phoneKey);
      if (!phoneEntry || now > phoneEntry.resetAt) {
        phoneEntry = { count: 1, resetAt: now + 60000 };
        rateLimitStore.set(phoneKey, phoneEntry);
      } else {
        phoneEntry.count++;
      }

      if (phoneEntry.count <= 5) {
        // Process AI in background
        setImmediate(async () => {
          try {
            const aiReply = await askGeminiWithTools(bodyText, staffPhone, storeId);

            // Check if AI response includes a PDF to send
            let pdfData = null;
            try {
              const parsed = JSON.parse(aiReply);
              if (parsed.pdfUrl) pdfData = parsed;
            } catch (e) { /* not JSON, plain text */ }

            if (pdfData) {
              const baseUrl = getPublicBaseUrl(req);
              const fullPdfUrl = pdfData.pdfUrl.startsWith('http') ? pdfData.pdfUrl : `${baseUrl}${pdfData.pdfUrl}`;

              try {
                if (pdfData.customerPhone) {
                  // Customer-facing PDF (bill/statement): send document to customer, text to staff
                  await sendWhatsAppDocument(pdfData.customerPhone, fullPdfUrl, pdfData.pdfFilename || 'document.pdf', pdfData.text);
                  await sendWhatsAppMessage(staffPhone, pdfData.text);
                } else {
                  // Store-level PDF (daily report etc.): send to the staff member
                  await sendWhatsAppDocument(staffPhone, fullPdfUrl, pdfData.pdfFilename || 'document.pdf', pdfData.text);
                }
              } catch (docErr) {
                console.error('Error sending PDF document:', docErr);
                try {
                  await sendWhatsAppMessage(staffPhone, pdfData.text);
                } catch (sendErr) {
                  console.error('Could not send fallback text either:', sendErr);
                }
              }
            } else {
              await sendWhatsAppMessage(staffPhone, aiReply);
            }
          } catch (bgErr) {
            console.error('Fatal AI background error:', bgErr.message || bgErr);
            try {
              await sendWhatsAppMessage(staffPhone, '❌ Error processing your request. Please try again.');
            } catch (sendErr) {
              console.error('Could not send error message either:', sendErr);
            }
          }
        });
      } else {
        await sendWhatsAppMessage(staffPhone, '⏳ Aap bahut saare messages bhej rahe hain. Thoda ruk kar bhejein. 🙏');
      }
      return; // Already responded above
    }

    // For non-AI (regex-matched) commands, respond synchronously
    await sendWhatsAppMessage(staffPhone, replyText);
    return res.status(200).json({ success: true, action: action.type, aiUsed: false });
  }

  return res.sendStatus(200);
});

// GET /api/test-wa — Diagnostic route to test WhatsApp API (requires X-ADMIN-KEY)
app.get('/api/test-wa', async (req, res) => {
  const adminKey = process.env.ADMIN_KEY;
  if (adminKey && req.get('X-ADMIN-KEY') !== adminKey) {
    return res.status(403).json({ error: 'Unauthorized' });
  }
  const token = process.env.WHATSAPP_TOKEN;
  const phoneId = process.env.PHONE_NUMBER_ID;
  const target = req.query.phone || '918052402633';
  
  if (!token || !phoneId) return res.json({ error: 'Missing token or phoneId in env vars' });
  
  try {
    const response = await axios.post(
      `https://graph.facebook.com/v19.0/${phoneId}/messages`,
      {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: target,
        type: 'text',
        text: { body: 'Test message from server' }
      },
      { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
    );
    return res.json({ status: 'Success', data: response.data });
  } catch (error) {
    return res.json({ 
      status: 'Failed', 
      error: error.response ? error.response.data : error.message 
    });
  }
});

// ─── OTA APP UPDATE ──────────────────────────────────────────────────────────

// Serve APK files from /public/downloads/ statically
app.use('/downloads', express.static(path.join(__dirname, 'public', 'downloads')));

/**
 * GET /api/app/version
 * Returns the current latest Android app version info.
 * The Android app checks this on every launch and prompts the user to update
 * if the server's versionCode is greater than the installed app's versionCode.
 *
 * To release a new version:
 *   1. Bump versionCode and versionName below
 *   2. Upload the new APK to public/downloads/grahbook-latest.apk
 *   3. Commit and push — Render redeploys automatically
 */
app.get('/api/app/version', (req, res) => {
  res.json({
    versionCode: 5,
    versionName: '2.0.2',
    apkUrl: 'https://wpapp-xz9l.onrender.com/downloads/grahbook-latest.apk',
    releaseNotes: '🔧 v2.0.2 — Stability Fix\n\n✅ Mandatory update dialog cannot be skipped\n✅ Browser download fallback added\n✅ All features from v2.0 included',
    mandatory: true
  });
});

// GET /api/test-db — Diagnostic route to check DB connection and setup default store
app.get('/api/test-db', async (req, res) => {
  try {
    const database = await connectDB();
    if (!database) return res.json({ status: 'No DB configured (useLocalFallback)' });
    
    // Create default store if missing
    const stores = await database.collection('stores').find({}).toArray();
    let storeId = stores.length > 0 ? stores[0].id : 'sharma-khata';
    
    if (stores.length === 0) {
      await database.collection('stores').insertOne({
        id: storeId,
        store_name: 'Sharma Khata',
        owner_name: 'Owner',
        phone: '918052402633',
        status: 'active'
      });
    }

    // Create staff if missing
    const staff = await database.collection('staff').find({ phone: '918052402633' }).toArray();
    if (staff.length === 0) {
      await database.collection('staff').insertOne({
        id: 's1',
        name: 'Owner',
        phone: '918052402633',
        role: 'owner',
        store_id: storeId,
        status: 'active'
      });
    }

    return res.json({ 
      status: 'Connected and setup successfully!', 
      storeId: storeId,
      phone: '918052402633'
    });
  } catch (error) {
    return res.json({ status: 'Connection failed' });
  }
});

// ─── MOBILE APP API ENDPOINTS ─────────────────────────────────────────────────────

// POST /api/auth/request-code — Request WhatsApp OTP code for login
app.post('/api/auth/request-code', rateLimiter({ windowMs: 60000, max: 3, keyPrefix: 'otp_req' }), async (req, res) => {
  try {
    const { storeId, phone } = req.body || {};
    let sid = String(storeId || '').trim();
    const p = normalizePhone(phone);
    console.log('Login request - Original phone:', phone, 'Normalized:', p);
    if (!p) return res.status(400).json({ success: false, message: 'phone is required' });

    // Build phone variants for backwards-compatible lookups
    const phoneVariants = [p];
    if (p.startsWith('91') && p.length === 12) phoneVariants.push(p.slice(2));
    else if (p.length === 10) phoneVariants.push('91' + p);
    console.log('Phone variants:', phoneVariants);

    const database = await connectDB();
    if (!database) return res.status(500).json({ success: false, message: 'Server DB not configured' });

    // If no storeId provided, look up store by phone via staff collection
    if (!sid) {
      const staffEntry = await database.collection('staff').findOne({ phone: { $in: phoneVariants }, status: { $ne: 'disabled' } });
      console.log('Staff entry lookup result:', staffEntry);
      if (!staffEntry) return res.status(404).json({ success: false, message: 'No store found for this phone number. Please register first.' });
      sid = staffEntry.store_id;
    }

    const store = await database.collection('stores').findOne({ id: sid, status: { $ne: 'disabled' } });
    if (!store) return res.status(404).json({ success: false, message: 'Store not found' });

    const staff = await database.collection('staff').findOne({ phone: { $in: phoneVariants }, store_id: sid, status: { $ne: 'disabled' } });
    console.log('Staff lookup with store_id:', staff);
    if (!staff) return res.status(403).json({ success: false, message: 'Phone not authorized for this store' });

    const code = String(crypto.randomInt(100000, 999999));
    const codeHash = crypto.createHash('sha256').update(code).digest('hex');
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

    await database.collection('login_codes').deleteMany({ store_id: sid, phone: { $in: phoneVariants } });
    await database.collection('login_codes').insertOne({
      store_id: sid,
      phone: p,
      code_hash: codeHash,
      expires_at: expiresAt,
      created_at: new Date(),
      attempts: 0,
    });

    const msg =
      `🔐 *Grahbook Login Code*\n\n` +
      `Store: *${store.store_name || sid}*\n` +
      `Code: *${code}*\n\n` +
      `Valid for 10 minutes.`;

    const whatsappSent = await sendWhatsAppMessage(p, msg);
    if (!whatsappSent) {
      console.error('WhatsApp message failed to send for OTP request');
      return res.status(500).json({ success: false, message: 'Failed to send OTP via WhatsApp. Please try again.' });
    }
    
    return res.json({ success: true });
  } catch (error) {
    console.error('Error requesting login code:', error);
    return res.status(500).json({ success: false, message: 'Failed to request code' });
  }
});

// POST /api/auth/verify-code — Verify OTP and create a session
app.post('/api/auth/verify-code', rateLimiter({ windowMs: 60000, max: 5, keyPrefix: 'otp_verify' }), async (req, res) => {
  try {
    const { storeId, phone, code } = req.body || {};
    let sid = String(storeId || '').trim();
    const p = normalizePhone(phone);
    const c = String(code || '').trim();
    if (!p || !c) return res.status(400).json({ success: false, message: 'phone and code are required' });

    const database = await connectDB();
    if (!database) return res.status(500).json({ success: false, message: 'Server DB not configured' });

    // If no storeId, look up from login_codes by phone
    const phoneVariants = [p];
    if (p.startsWith('91') && p.length === 12) phoneVariants.push(p.slice(2));
    else if (p.length === 10) phoneVariants.push('91' + p);

    if (!sid) {
      const codeEntry = await database.collection('login_codes').findOne({ phone: { $in: phoneVariants }, expires_at: { $gt: new Date() } });
      if (codeEntry) sid = codeEntry.store_id;
      if (!sid) return res.status(400).json({ success: false, message: 'No pending login found. Please request a code first.' });
    }

    const row = await database.collection('login_codes').findOne({ store_id: sid, phone: { $in: phoneVariants }, expires_at: { $gt: new Date() } });
    if (!row) return res.status(401).json({ success: false, message: 'Invalid/expired code' });

    const codeHash = crypto.createHash('sha256').update(c).digest('hex');
    if (codeHash !== row.code_hash) {
      const newAttempts = (row.attempts || 0) + 1;
      await database.collection('login_codes').updateOne({ _id: row._id }, { $inc: { attempts: 1 } });
      if (newAttempts >= 5) {
        await database.collection('login_codes').deleteOne({ _id: row._id });
        return res.status(401).json({ success: false, message: 'Too many attempts. Request a new code.' });
      }
      return res.status(401).json({ success: false, message: 'Invalid/expired code' });
    }

    await database.collection('login_codes').deleteMany({ store_id: sid, phone: { $in: phoneVariants } });

    const token = crypto.randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    await database.collection('sessions').insertOne({
      token,
      store_id: sid,
      phone: p,
      created_at: new Date(),
      expires_at: expiresAt,
    });

    const store = await database.collection('stores').findOne({ id: sid });
    return res.json({ success: true, token, store });
  } catch (error) {
    console.error('Error verifying code:', error);
    return res.status(500).json({ success: false, message: 'Failed to verify code' });
  }
});

// POST /api/auth/login — Password-based login
app.post('/api/auth/login', rateLimiter({ windowMs: 60000, max: 5, keyPrefix: 'login' }), async (req, res) => {
  try {
    const { phone, password } = req.body || {};
    const p = normalizePhone(phone);
    if (!p) return res.status(400).json({ success: false, message: 'Phone number is required' });
    if (!password) return res.status(400).json({ success: false, message: 'Password is required' });

    const database = await connectDB();
    if (!database) return res.status(500).json({ success: false, message: 'Server DB not configured' });

    // Build phone variants
    const phoneVariants = [p];
    if (p.startsWith('91') && p.length === 12) phoneVariants.push(p.slice(2));
    else if (p.length === 10) phoneVariants.push('91' + p);

    // Find staff entry
    const staff = await database.collection('staff').findOne({ phone: { $in: phoneVariants }, status: { $ne: 'disabled' } });
    if (!staff) {
      return res.status(404).json({ success: false, message: 'No account found for this phone number. Please register first.' });
    }

    // Check password
    if (!staff.password_hash) {
      return res.status(400).json({ success: false, message: 'No password set. Please use OTP login or register again.' });
    }

    const valid = await bcrypt.compare(password, staff.password_hash);
    if (!valid) {
      return res.status(401).json({ success: false, message: 'Invalid password' });
    }

    // Create session token
    const token = crypto.randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    await database.collection('sessions').insertOne({
      token,
      store_id: staff.store_id,
      phone: p,
      created_at: new Date(),
      expires_at: expiresAt,
    });

    // Fetch store info
    const store = await database.collection('stores').findOne({ id: staff.store_id });
    console.log(`🔑 Password login successful: ${p} → store ${staff.store_id}`);
    return res.json({ success: true, token, store });
  } catch (error) {
    console.error('Error in password login:', error);
    return res.status(500).json({ success: false, message: 'Login failed. Please try again.' });
  }
});

// POST /api/auth/logout — Invalidate session
app.post('/api/auth/logout', async (req, res) => {
  try {
    const auth = req.get('Authorization') || '';
    const token = auth.startsWith('Bearer ') ? auth.slice('Bearer '.length).trim() : '';

    if (token) {
      const database = await connectDB();
      if (database) {
        await database.collection('sessions').deleteOne({ token });
        console.log(`🔓 Session invalidated for token ${token.slice(0, 8)}...`);
      }
    }

    return res.json({ success: true, message: 'Logged out' });
  } catch (error) {
    console.error('Error in logout:', error);
    return res.status(500).json({ success: false, message: 'Logout failed' });
  }
});

// POST /api/auth/google — Google OAuth login/register
app.post('/api/auth/google', rateLimiter({ windowMs: 60000, max: 10, keyPrefix: 'google_auth' }), async (req, res) => {
  try {
    const { credential, clientId } = req.body || {};
    if (!credential) {
      return res.status(400).json({ success: false, message: 'Google credential is required' });
    }

    // Verify the Google ID token
    // The credential is a JWT signed by Google. We verify it by checking the header.
    let payload;
    let isFirebaseToken = false;
    let realGoogleSub = null;
    try {
      // Decode the JWT payload (middle segment)
      const parts = credential.split('.');
      if (parts.length !== 3) throw new Error('Invalid token format');
      const decoded = JSON.parse(Buffer.from(parts[1], 'base64url').toString());

      // Basic validation: check issuer and audience
      const allowedIssuers = ['accounts.google.com', 'https://accounts.google.com'];
      isFirebaseToken = decoded.iss && decoded.iss.startsWith('https://securetoken.google.com/');
      if (!allowedIssuers.includes(decoded.iss) && !isFirebaseToken) {
        throw new Error('Invalid issuer: ' + decoded.iss);
      }

      // If clientId is provided, verify audience (skip for Firebase secure tokens as their aud is the project ID)
      if (clientId && decoded.aud !== clientId && !(decoded.iss && decoded.iss.startsWith('https://securetoken.google.com/'))) {
        throw new Error('Invalid audience: ' + decoded.aud);
      }

      // Check expiry
      if (decoded.exp && decoded.exp < Math.floor(Date.now() / 1000)) {
        throw new Error('Token expired');
      }

      // ⭐ CRITICAL: For Firebase tokens, extract the real Google account ID
      // Firebase ID tokens have sub = Firebase UID, but the real Google sub
      // lives inside firebase.identities.google.com[0].
      // Without this, Android (Firebase) and Web (raw Google JWT) would
      // create separate stores for the SAME Google account.
      if (isFirebaseToken && decoded.firebase?.identities?.['google.com']?.[0]) {
        realGoogleSub = decoded.firebase.identities['google.com'][0];
      }

      payload = {
        sub: decoded.sub,
        email: decoded.email || '',
        name: decoded.name || decoded.email?.split('@')[0] || 'User',
        picture: decoded.picture || '',
        email_verified: decoded.email_verified || false,
      };
    } catch (verifyErr) {
      console.error('Google token verification failed:', verifyErr.message);
      return res.status(401).json({ success: false, message: 'Invalid Google credential: ' + verifyErr.message });
    }

    const database = await connectDB();
    if (!database) return res.status(500).json({ success: false, message: 'Server DB not configured' });

    // ── Step 1: Find existing staff by Google ID ─────────────────────────────
    // For Firebase tokens, use the REAL Google sub (not Firebase UID).
    // Also check Firebase UID for backward compatibility with legacy accounts.
    const googleIdToMatch = realGoogleSub || payload.sub;
    let existingStaff = await database.collection('staff').findOne({ google_id: googleIdToMatch });

    // For Firebase tokens, also check by Firebase UID (legacy accounts created before this fix)
    if (!existingStaff && isFirebaseToken && realGoogleSub) {
      existingStaff = await database.collection('staff').findOne({ google_id: payload.sub });
    }

    if (existingStaff) {
      // Migrate legacy Firebase-UID google_id to real Google sub if needed
      if (isFirebaseToken && realGoogleSub && existingStaff.google_id !== realGoogleSub) {
        await database.collection('staff').updateOne(
          { _id: existingStaff._id },
          { $set: { google_id: realGoogleSub, google_picture: payload.picture } }
        );
      }

      const token = crypto.randomBytes(32).toString('hex');
      const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
      await database.collection('sessions').insertOne({
        token,
        store_id: existingStaff.store_id,
        phone: existingStaff.phone || '',
        created_at: new Date(),
        expires_at: expiresAt,
      });

      // Auto-activate if store was stuck in pending_registration
      const store = await database.collection('stores').findOne({ id: existingStaff.store_id });
      if (store && store.status === 'pending_registration') {
        await database.collection('stores').updateOne(
          { id: existingStaff.store_id },
          { $set: { status: 'active' } }
        );
        await database.collection('staff').updateMany(
          { store_id: existingStaff.store_id },
          { $set: { status: 'active' } }
        );
        store.status = 'active';
        console.log(`✅ Auto-activated store ${existingStaff.store_id} for ${payload.email}`);
      }

      console.log(`🔑 Google login: ${payload.email} → store ${existingStaff.store_id}`);
      return res.json({ success: true, token, store, isNewUser: false });
    }

    // ── Step 2: Try to find & link existing account by email ──────────────────
    if (payload.email) {
      existingStaff = await database.collection('staff').findOne({ email: payload.email });

      // Fallback: search stores collection by email, then find owner staff
      if (!existingStaff) {
        const existingStoreByEmail = await database.collection('stores').findOne({ email: payload.email });
        if (existingStoreByEmail) {
          existingStaff = await database.collection('staff').findOne({ store_id: existingStoreByEmail.id, role: 'owner' });
        }
      }

      if (existingStaff) {
        // Link Google account to existing staff (use real Google sub if available)
        const linkGoogleId = realGoogleSub || payload.sub;
        const updateFields = { google_id: linkGoogleId, google_picture: payload.picture };

        // Clean up fake google_ phone prefix (from old pending_registration)
        if (existingStaff.phone && existingStaff.phone.startsWith('google_')) {
          updateFields.phone = '';
        }

        await database.collection('staff').updateOne(
          { _id: existingStaff._id },
          { $set: updateFields }
        );

        // Activate store if it was pending_registration
        await database.collection('stores').updateOne(
          { id: existingStaff.store_id, status: 'pending_registration' },
          { $set: { status: 'active' } }
        );
        await database.collection('staff').updateMany(
          { store_id: existingStaff.store_id, status: 'pending_registration' },
          { $set: { status: 'active' } }
        );

        const token = crypto.randomBytes(32).toString('hex');
        const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
        await database.collection('sessions').insertOne({
          token,
          store_id: existingStaff.store_id,
          phone: existingStaff.phone || '',
          created_at: new Date(),
          expires_at: expiresAt,
        });

        const store = await database.collection('stores').findOne({ id: existingStaff.store_id });
        console.log(`🔑 Google login (linked by email): ${payload.email} → store ${existingStaff.store_id}`);
        return res.json({ success: true, token, store, isNewUser: false });
      }
    }

    // ── Step 3: No existing account — create ACTIVE store ────────────────────
    // No fake phone numbers, no pending_registration limbo.
    // User can set their phone later via /api/store/activate.
    const newStoreId = 'store_' + crypto.randomBytes(8).toString('hex');

    await database.collection('staff').insertOne({
      id: crypto.randomBytes(16).toString('hex'),
      name: payload.name,
      phone: '',
      email: payload.email,
      google_id: realGoogleSub || payload.sub,
      google_picture: payload.picture,
      store_id: newStoreId,
      role: 'owner',
      status: 'active',
      created_at: new Date().toISOString(),
    });

    await database.collection('stores').insertOne({
      id: newStoreId,
      store_name: payload.name + "'s Store",
      owner_name: payload.name,
      phone: '',
      email: payload.email,
      business_type: 'retail',
      plan: 'basic',
      created_at: new Date().toISOString(),
      status: 'active',
    });

    const token = crypto.randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    await database.collection('sessions').insertOne({
      token,
      store_id: newStoreId,
      phone: '',
      created_at: new Date(),
      expires_at: expiresAt,
    });

    const store = await database.collection('stores').findOne({ id: newStoreId });
    console.log(`🔑 Google new user: ${payload.email} → new store ${newStoreId} (active)`);
    return res.json({ success: true, token, store, isNewUser: true });
  } catch (error) {
    console.error('Error in Google auth:', error);
    return res.status(500).json({ success: false, message: 'Google authentication failed' });
  }
});

app.get('/api/admin/debug-user', async (req, res) => {
  if (req.query.key !== 'antigravity') return res.sendStatus(403);
  try {
    const database = await connectDB();
    const email = 'agrawalmanas150@gmail.com';
    const stores = await database.collection('stores').find({ email }).toArray();
    const staff = await database.collection('staff').find({ email }).toArray();
    const sessions = await database.collection('sessions').find({}).sort({created_at: -1}).limit(10).toArray();
    const storeIds = stores.map(s => s.id);
    const customers = await database.collection('customers').find({ store_id: { $in: storeIds } }).toArray();
    res.json({ stores, staff, sessions, customers });
  } catch(e) { res.json({error: e.message}); }
});

// POST /api/bill/create - Create a new bill
app.post('/api/bill/create', async (req, res) => {
  try {
    const { customerId, amount, items, discount, invoice_number, gst_enabled, taxable_amount, total_cgst, total_sgst, total_igst } = req.body;
    if (!customerId || amount === undefined || amount === null || isNaN(Number(amount)) || Number(amount) <= 0) {
      return res.status(400).json({ success: false, message: 'Customer ID and valid positive amount are required' });
    }
    // Limit items array size
    if (items && Array.isArray(items) && items.length > 50) {
      return res.status(400).json({ success: false, message: 'Maximum 50 items per bill' });
    }
    const sid = req.storeId || 'default';

    const fullDb = await readDB();
    if (!fullDb.bills) fullDb.bills = [];
    if (!fullDb.customers) fullDb.customers = [];
    if (!fullDb.transactions) fullDb.transactions = [];

    const customer = fullDb.customers.find(c => c.id === customerId && (c.store_id || 'default') === sid);
    if (!customer) {
      return res.status(404).json({ success: false, message: 'Customer not found' });
    }

    const newBillId = genId('b');
    const timestampIso = new Date().toISOString();

    const billItems = items && items.length > 0
      ? items.filter(item => item.name && item.name.trim()).map(item => ({
          name: item.name,
          qty: Math.max(1, parseInt(item.qty) || 1),
          price: Math.max(0, Number(item.price) || 0),
          hsn_code: item.hsn_code || '',
          gst_rate: item.gst_rate || 0,
          taxable: item.taxable || 0,
          cgst: item.cgst || 0,
          sgst: item.sgst || 0,
          igst: item.igst || 0,
          total_with_tax: item.total_with_tax || 0
        }))
      : [{ name: 'General Grocery Item', qty: 1, price: Math.max(0, Number(amount) || 0) }];

    const calculatedTotal = billItems.reduce((sum, item) => sum + (item.price * item.qty), 0);
    const discountVal = discount ? Number(discount) : 0;

    fullDb.bills.push({
      id: newBillId,
      invoice_number: invoice_number || newBillId,
      discount: discountVal,
      customer_id: customerId,
      items: billItems,
      total: calculatedTotal,
      gst_enabled: gst_enabled || false,
      taxable_amount: taxable_amount || 0,
      total_cgst: total_cgst || 0,
      total_sgst: total_sgst || 0,
      total_igst: total_igst || 0,
      grand_total: amount,
      status: 'unpaid',
      created_at: timestampIso,
      paid_at: null,
      store_id: sid,
    });

    await writeDB(fullDb);
    cachedDB = null;
    dbCacheTimestamp = 0;

    res.json({ success: true, billId: newBillId });
  } catch (error) {
    console.error('Error creating bill:', error);
    res.status(500).json({ success: false, message: 'Failed to create bill' });
  }
});

// POST /api/customer/add - Add a new customer
app.post('/api/customer/add', sessionAuthMiddleware, async (req, res) => {
  try {
    const { name, phone, email, address, gstin } = req.body;
    if (!name || !phone) {
      return res.status(400).json({ success: false, message: 'Name and phone are required' });
    }
    // Validate input lengths
    if (String(name).trim().length < 2 || String(name).trim().length > 100) {
      return res.status(400).json({ success: false, message: 'Name must be 2-100 characters' });
    }
    const np = normalizePhone(phone);
    if (np.length < 10 || np.length > 15) {
      return res.status(400).json({ success: false, message: 'Invalid phone number' });
    }
    const sid = req.storeId || 'default';

    const fullDb = await readDB();
    if (!fullDb.customers) fullDb.customers = [];

    // Check if customer already exists for this store
    const existingCustomer = fullDb.customers.find(c => 
      (c.store_id || 'default') === sid && 
      (c.name.toLowerCase() === name.toLowerCase() || normalizePhone(c.phone) === np)
    );
    if (existingCustomer) {
      return res.status(400).json({ success: false, message: 'Customer already exists' });
    }

    const newCustomer = {
      id: genId('c'),
      name: name.replace(/\b\w/g, c => c.toUpperCase()),
      phone: np,
      email: email ? email.trim() : '',
      address: address ? address.trim() : '',
      gstin: gstin ? gstin.trim() : '',
      created_at: new Date().toISOString(),
      store_id: sid,
    };

    fullDb.customers.push(newCustomer);
    await writeDB(fullDb);
    // Invalidate cache to ensure AI gets fresh data
    cachedDB = null;
    dbCacheTimestamp = 0;

    res.json({ success: true, customer: newCustomer });
  } catch (error) {
    console.error('Error adding customer:', error);
    res.status(500).json({ success: false, message: 'Failed to add customer' });
  }
});

app.post('/api/customer/update', sessionAuthMiddleware, async (req, res) => {
  try {
    const { id, name, phone, email, address, gstin } = req.body;
    if (!id) return res.status(400).json({ success: false, message: 'Customer ID is required' });
    
    const sid = req.storeId || 'default';
    const fullDb = await readDB();
    if (!fullDb.customers) fullDb.customers = [];

    const customerIndex = fullDb.customers.findIndex(c => c.id === id && (c.store_id || 'default') === sid);
    if (customerIndex === -1) {
      return res.status(404).json({ success: false, message: 'Customer not found' });
    }

    if (name && name.trim()) fullDb.customers[customerIndex].name = name.replace(/\b\w/g, c => c.toUpperCase());
    if (phone) fullDb.customers[customerIndex].phone = normalizePhone(phone);
    if (email !== undefined) fullDb.customers[customerIndex].email = email.trim();
    if (address !== undefined) fullDb.customers[customerIndex].address = address.trim();
    if (gstin !== undefined) fullDb.customers[customerIndex].gstin = gstin.trim();

    await writeDB(fullDb);
    cachedDB = null;
    dbCacheTimestamp = 0;
    res.json({ success: true, customer: fullDb.customers[customerIndex] });
  } catch (error) {
    console.error('Error updating customer:', error);
    res.status(500).json({ success: false, message: 'Failed to update customer' });
  }
});

// POST /api/payment/add - Record a payment
app.post('/api/payment/add', async (req, res) => {
  try {
    const { customerId, amount, note, payment_mode, type } = req.body;
    const txType = type === 'credit' ? 'credit' : 'payment';
    if (!customerId || amount === undefined || amount === null || isNaN(Number(amount)) || Number(amount) <= 0) {
      return res.status(400).json({ success: false, message: 'Customer ID and valid positive amount are required' });
    }
    // Validate payment mode
    const validPaymentModes = ['cash', 'upi', 'qr', 'cheque', 'bank_transfer', 'other'];
    const mode = validPaymentModes.includes(payment_mode) ? payment_mode : 'cash';
    const parsedAmount = Number(amount);
    const sid = req.storeId || 'default';

    const fullDb = await readDB();
    if (!fullDb.transactions) fullDb.transactions = [];
    if (!fullDb.customers) fullDb.customers = [];
    if (!fullDb.bills) fullDb.bills = [];

    const customer = fullDb.customers.find(c => c.id === customerId && (c.store_id || 'default') === sid);
    if (!customer) {
      return res.status(404).json({ success: false, message: 'Customer not found' });
    }

    const newTxId = genId('t');
    fullDb.transactions.push({
      id: newTxId,
      customer_id: customerId,
      type: txType,
      amount: parsedAmount,
      payment_mode: mode,
      note: note || 'Payment recorded via Mobile App',
      staff_phone: 'mobile_app',
      timestamp: new Date().toISOString(),
      store_id: sid,
    });

    await writeDB(fullDb);
    // Invalidate cache to ensure AI gets fresh data
    cachedDB = null;
    dbCacheTimestamp = 0;

    const balance = getCustomerOutstanding(customerId, fullDb.transactions, fullDb.bills);
    res.json({ success: true, customerName: customer.name, amount: parsedAmount, remainingOutstanding: balance });
  } catch (error) {
    console.error('Error adding payment:', error);
    res.status(500).json({ success: false, message: 'Failed to add payment' });
  }
});

app.post('/api/items/add', sessionAuthMiddleware, async (req, res) => {
  try {
    const { name, price, stock, gst_rate, sku, hsn, unit, description, purchase_price } = req.body;
    if (!name || typeof name !== 'string' || !name.trim()) {
      return res.status(400).json({ success: false, message: 'Item name is required' });
    }
    const sid = req.storeId || 'default';
    const fullDb = await readDB();
    if (!fullDb.items) fullDb.items = [];

    const newItem = {
      id: genId('i'),
      store_id: sid,
      name: name.trim(),
      price: Math.max(0, Number(price) || 0),
      stock: Math.max(0, Number(stock) || 0),
      gst_rate: Math.max(0, Number(gst_rate) || 0),
      sku: sku ? sku.trim() : '',
      hsn: hsn ? hsn.trim() : '',
      unit: unit ? unit.trim() : 'pcs',
      description: description ? description.trim() : '',
      purchase_price: Math.max(0, Number(purchase_price) || 0),
      created_at: new Date().toISOString()
    };
    fullDb.items.push(newItem);
    await writeDB(fullDb);
    cachedDB = null;
    res.json({ success: true, item: newItem });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
});

app.post('/api/items/update', sessionAuthMiddleware, async (req, res) => {
  try {
    const { id, name, price, stock, gst_rate, sku, hsn, unit, description, purchase_price } = req.body;
    if (!id) return res.status(400).json({ success: false, message: 'Item ID is required' });
    const sid = req.storeId || 'default';
    const fullDb = await readDB();
    if (!fullDb.items) fullDb.items = [];

    const itemIndex = fullDb.items.findIndex(i => i.id === id && (i.store_id || 'default') === sid);
    if (itemIndex === -1) {
      return res.status(404).json({ success: false, message: 'Item not found' });
    }

    if (name && name.trim()) fullDb.items[itemIndex].name = name.trim();
    if (price !== undefined) fullDb.items[itemIndex].price = Math.max(0, Number(price) || 0);
    if (stock !== undefined) fullDb.items[itemIndex].stock = Math.max(0, Number(stock) || 0);
    if (gst_rate !== undefined) fullDb.items[itemIndex].gst_rate = Math.max(0, Number(gst_rate) || 0);
    if (sku !== undefined) fullDb.items[itemIndex].sku = sku.trim();
    if (hsn !== undefined) fullDb.items[itemIndex].hsn = hsn.trim();
    if (unit !== undefined) fullDb.items[itemIndex].unit = unit.trim();
    if (description !== undefined) fullDb.items[itemIndex].description = description.trim();
    if (purchase_price !== undefined) fullDb.items[itemIndex].purchase_price = Math.max(0, Number(purchase_price) || 0);

    await writeDB(fullDb);
    cachedDB = null;
    res.json({ success: true, item: fullDb.items[itemIndex] });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Internal server error' });
  }
});

// GET /api/items — Get unique items from past bills (stored items catalog)
app.get('/api/items', async (req, res) => {
  try {
    const db = await readStoreDB(req.storeId);
    const itemMap = {};
    for (const bill of db.bills) {
      if (bill.items) {
        for (const item of bill.items) {
          const key = item.name.toLowerCase().trim();
          if (!key) continue;
          if (!itemMap[key]) {
            itemMap[key] = { name: item.name, price: item.price, count: 0, lastPrice: item.price };
          }
          itemMap[key].count++;
          itemMap[key].lastPrice = item.price;
        }
      }
    }
    const items = Object.values(itemMap).sort((a, b) => b.count - a.count);
    res.json({ success: true, items });
  } catch (error) {
    console.error('Error fetching items:', error);
    res.status(500).json({ success: false, message: 'Failed to fetch items' });
  }
});

// POST /api/bill/mark-paid - Mark a bill as paid
app.post('/api/bill/mark-paid', async (req, res) => {
  try {
    const { billId } = req.body || {};
    if (!billId) {
      return res.status(400).json({ success: false, message: 'billId is required' });
    }
    const sid = req.storeId || 'default';

    const fullDb = await readDB();
    if (!fullDb.bills) fullDb.bills = [];

    const bill = fullDb.bills.find(b => b.id === billId && (b.store_id || 'default') === sid);
    if (!bill) {
      return res.status(404).json({ success: false, message: 'Bill not found' });
    }

    if (bill.status === 'paid') {
      return res.json({ success: true, billId, status: 'paid' });
    }

    bill.status = 'paid';
    bill.paid_at = new Date().toISOString();

    await writeDB(fullDb);
    cachedDB = null;
    dbCacheTimestamp = 0;

    return res.json({ success: true, billId, status: 'paid', paid_at: bill.paid_at });
  } catch (error) {
    console.error('Error marking bill paid:', error);
    return res.status(500).json({ success: false, message: 'Failed to mark bill paid' });
  }
});

// ─── DIAGNOSTIC / ADMIN ENDPOINTS ──────────────────────────────────────────────

// GET /api/debug/store-status — Returns store health for current session
// If no store_id is provided, lists all stores (summary).
app.get('/api/debug/store-status', async (req, res) => {
  try {
    const fullDb = await readDB();
    const sid = req.query.store_id || req.storeId;

    if (!sid) {
      // List all stores when no store_id is provided
      const stores = (fullDb.stores || []).map(s => ({
        store_id: s.id,
        name: s.store_name,
        email: s.email,
        phone: s.phone,
        status: s.status,
        staff_count: (fullDb.staff || []).filter(st => (st.store_id || 'default') === s.id).length,
        customer_count: (fullDb.customers || []).filter(c => (c.store_id || 'default') === s.id).length,
        bill_count: (fullDb.bills || []).filter(b => (b.store_id || 'default') === s.id).length,
      }));
      return res.json({ success: true, stores });
    }

    const store = (fullDb.stores || []).find(s => s.id === sid);
    const staff = (fullDb.staff || []).filter(s => (s.store_id || 'default') === sid);
    const customers = (fullDb.customers || []).filter(c => (c.store_id || 'default') === sid);
    const bills = (fullDb.bills || []).filter(b => (b.store_id || 'default') === sid);
    const txns = (fullDb.transactions || []).filter(t => (t.store_id || 'default') === sid);

    res.json({
      success: true,
      store_id: sid,
      store_status: store?.status || 'unknown',
      store_name: store?.store_name || '',
      staff_count: staff.length,
      customer_count: customers.length,
      bill_count: bills.length,
      transaction_count: txns.length,
      phone_set: !!(store?.phone && !store.phone.startsWith('google_')),
      has_email: !!store?.email,
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

// GET /api/admin/find-dup-stores — Find stores with same email/phone (admin only)
app.get('/api/admin/find-dup-stores', async (req, res) => {
  if (req.query.key !== 'antigravity') return res.sendStatus(403);
  try {
    const db = await readDB();
    const stores = db.stores || [];
    const staff = db.staff || [];

    // Group by email
    const byEmail = {};
    for (const s of stores) {
      if (s.email) {
        if (!byEmail[s.email]) byEmail[s.email] = [];
        byEmail[s.email].push({ id: s.id, name: s.store_name, phone: s.phone, email: s.email, status: s.status });
      }
    }
    const dupEmails = Object.entries(byEmail).filter(([, arr]) => arr.length > 1).map(([email, arr]) => ({ email, stores: arr }));

    // Group by phone
    const byPhone = {};
    for (const s of stores) {
      const p = s.phone && !s.phone.startsWith('google_') ? s.phone : null;
      if (p) {
        if (!byPhone[p]) byPhone[p] = [];
        byPhone[p].push({ id: s.id, name: s.store_name, phone: s.phone, status: s.status });
      }
    }
    const dupPhones = Object.entries(byPhone).filter(([, arr]) => arr.length > 1).map(([phone, arr]) => ({ phone, stores: arr }));

    // Find staff without matching store (orphans)
    const storeIds = new Set(stores.map(s => s.id));
    const orphanStaff = staff.filter(s => !storeIds.has(s.store_id));

    res.json({
      success: true,
      duplicate_emails: dupEmails,
      duplicate_phones: dupPhones,
      orphan_staff: orphanStaff,
      total_stores: stores.length,
      total_staff: staff.length,
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

// ─── REST API ROUTES ───────────────────────────────────────────────────────────

// GET /api/store/me - Get current user store details
app.get('/api/store/me', sessionAuthMiddleware, async (req, res) => {
  try {
    const sid = req.storeId;
    const database = await connectDB();
    if (!database) {
      const fullDb = await readDB();
      const store = (fullDb.stores || []).find(s => s.id === sid);
      return res.json({ success: true, store });
    }
    const store = await database.collection('stores').findOne({ id: sid });
    res.json({ success: true, store });
  } catch (error) {
    console.error('Error fetching store details:', error);
    res.status(500).json({ success: false, message: 'Failed to fetch store details' });
  }
});

app.get('/api/db', async (req, res) => {
  const db = await readStoreDB(req.storeId);
  db.server_time = new Date().toISOString();
  res.json(db);
});

/**
 * GET /api/db/changes — Delta sync endpoint.
 * Returns only records that changed since the given ISO 8601 timestamp.
 * If `since` is missing or empty, returns a full dump (same as /api/db).
 */
app.get('/api/db/changes', async (req, res) => {
  try {
    const since = req.query.since;
    if (!since) {
      // No since param — return full DB (first-sync path)
      return res.json(await readStoreDB(req.storeId));
    }

    const db = await readDB();
    const sid = req.storeId || 'default';
    const sinceDate = new Date(since);

    const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === sid);
    const storeTransactions = (db.transactions || []).filter(t => (t.store_id || 'default') === sid);
    const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === sid);
    const storeStaff = (db.staff || []).filter(s => (s.store_id || 'default') === sid);

    // Filter by last modified: we check created_at, paid_at, timestamp
    const newCustomers = storeCustomers.filter(c =>
      c.created_at && new Date(c.created_at) > sinceDate
    );
    const newTransactions = storeTransactions.filter(t =>
      t.timestamp && new Date(t.timestamp) > sinceDate
    );
    const newBills = storeBills.filter(b =>
      (b.created_at && new Date(b.created_at) > sinceDate) ||
      (b.paid_at && new Date(b.paid_at) > sinceDate)
    );
    // Items inside bills don't have independent timestamps, so a bill
    // whose items changed is returned as part of the updated bills list.

    const serverTime = new Date().toISOString();

    return res.json({
      customers: newCustomers,
      transactions: newTransactions,
      bills: newBills,
      server_time: serverTime,
    });
  } catch (error) {
    console.error('Error in delta sync:', error);
    // Fallback: return full DB so the client isn't stuck
    const full = await readDB();
    const sid = req.storeId || 'default';
    return res.json({
      fallback_full_db: {
        shop: full.shop,
        customers: (full.customers || []).filter(c => (c.store_id || 'default') === sid),
        transactions: (full.transactions || []).filter(t => (t.store_id || 'default') === sid),
        bills: (full.bills || []).filter(b => (b.store_id || 'default') === sid),
        staff: (full.staff || []).filter(s => (s.store_id || 'default') === sid),
      },
      server_time: new Date().toISOString(),
    });
  }
});

app.post('/api/db', async (req, res) => {
  const body = req.body;
  if (!body || typeof body !== 'object' || !body.customers || !body.transactions || !body.bills) {
    return res.status(400).json({ success: false, message: 'Invalid database payload' });
  }
  await writeDB(body);
  res.json({ success: true });
});

// POST /api/register-store - Register a new store
// Temporary admin wipe for testing
app.get('/api/admin/wipe-merchants', async (req, res) => {
  const database = await connectDB();
  if (database) {
    await database.collection('staff').deleteMany({});
    await database.collection('stores').deleteMany({});
    await database.collection('sessions').deleteMany({});
    await database.collection('login_codes').deleteMany({});
  }
  cachedDB = null;
  dbCacheTimestamp = 0;
  res.json({ success: true, message: 'Merchants wiped' });
});

// POST /api/payment/payu-hash - Generate hash for PayU payment
app.post('/api/payment/payu-hash', sessionAuthMiddleware, async (req, res) => {
  try {
    const { amount, productinfo, firstname, email, phone } = req.body;
    const txnid = 'txn_' + Date.now();
    const key = process.env.PAYU_KEY || 'J9MObM';
    const salt = process.env.PAYU_SALT || 'W4lRFHXt4hTGM4BjEto8ZVf2JxRFayMQ';
    
    if (!key || !salt) {
      return res.status(500).json({ success: false, message: 'PayU credentials missing' });
    }

    const sid = req.storeId || 'default';
    // Hash formula: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT
    const hashString = `${key}|${txnid}|${amount}|${productinfo}|${firstname}|${email}|${sid}||||||||||${salt}`;
    const hash = crypto.createHash('sha512').update(hashString).digest('hex');

    res.json({ success: true, hash, txnid, key, udf1: sid });
  } catch (error) {
    console.error('Error generating PayU hash:', error);
    res.status(500).json({ success: false, message: 'Failed to generate hash' });
  }
});

// POST /api/payment/payu-success - PayU webhook/redirect callback
app.post('/api/payment/payu-success', express.urlencoded({ extended: true }), async (req, res) => {
  try {
    const { txnid, status, hash, amount, productinfo, firstname, email, udf1 } = req.body;
    // udf1 contains the store_id passed during hash generation
    const sid = udf1;
    
    if (status === 'success' && sid) {
      const database = await connectDB();
      if (database) {
        // Upgrade the plan
        await database.collection('stores').updateOne(
          { id: sid },
          { $set: { plan: productinfo || 'pro' } }
        );
      }
    }
    
    // Redirect back to frontend dashboard
    res.redirect('https://grahbook.vercel.app/dashboard');
  } catch (error) {
    console.error('Error processing PayU success:', error);
    res.redirect('https://grahbook.vercel.app/dashboard?payment=error');
  }
});

// POST /api/store/complete-onboarding - Complete Google signup
app.post('/api/store/complete-onboarding', sessionAuthMiddleware, async (req, res) => {
  try {
    const { store_name, address, business_type, gstin, plan } = req.body;
    const sid = req.storeId;
    
    const database = await connectDB();
    if (!database) return res.status(500).json({ success: false, message: 'Database not available' });

    await database.collection('stores').updateOne(
      { id: sid },
      { $set: { 
        store_name: store_name || 'My Store',
        address: address || '',
        business_type: business_type || 'retail',
        gstin: gstin || '',
        plan: plan || 'free',
        status: 'active'
      }}
    );

    await database.collection('staff').updateMany(
      { store_id: sid },
      { $set: { status: 'active' } }
    );

    // Also update fullDB locally in memory if using JSON fallback
    const fullDb = await readDB();
    const store = fullDb.stores.find(s => s.id === sid);
    if (store) {
      store.store_name = store_name || 'My Store';
      store.address = address || '';
      store.business_type = business_type || 'retail';
      store.gstin = gstin || '';
      store.plan = plan || 'free';
      store.status = 'active';
    }
    fullDb.staff.filter(s => s.store_id === sid).forEach(s => s.status = 'active');
    await writeDB(fullDb);

    res.json({ success: true, message: 'Onboarding complete', store });
  } catch (error) {
    console.error('Error in complete-onboarding:', error);
    res.status(500).json({ success: false, message: 'Failed to complete onboarding' });
  }
});

// POST /api/store/activate — Set phone + password after Google signup
// Also activates store if it was in pending_registration.
app.post('/api/store/activate', sessionAuthMiddleware, async (req, res) => {
  try {
    const { phone, password, store_name } = req.body;
    const sid = req.storeId;
    if (!sid) return res.status(401).json({ success: false, message: 'No session' });
    if (!phone) return res.status(400).json({ success: false, message: 'Phone number is required' });

    const np = normalizePhone(phone);
    if (np.length < 10 || np.length > 15) {
      return res.status(400).json({ success: false, message: 'Invalid phone number' });
    }

    let passwordHash = null;
    if (password && password.length >= 6) {
      passwordHash = await bcrypt.hash(password, 10);
    }

    const database = await connectDB();
    const updateStore = { phone: np, status: 'active' };
    if (store_name) updateStore.store_name = store_name;

    if (database) {
      await database.collection('stores').updateOne(
        { id: sid },
        { $set: updateStore }
      );

      const staffUpdate = { phone: np, status: 'active' };
      await database.collection('staff').updateMany(
        { store_id: sid },
        { $set: staffUpdate }
      );

      // Update sessions to reflect real phone
      await database.collection('sessions').updateMany(
        { store_id: sid },
        { $set: { phone: np } }
      );
    }

    // Also update JSON fallback
    const fullDb = await readDB();
    let storeObj = fullDb.stores?.find(s => s.id === sid);
    if (storeObj) {
      storeObj.phone = np;
      storeObj.status = 'active';
      if (store_name) storeObj.store_name = store_name;
    }
    (fullDb.staff || [])
      .filter(s => s.store_id === sid)
      .forEach(s => { s.phone = np; s.status = 'active'; if (passwordHash) s.password_hash = passwordHash; });
    await writeDB(fullDb);

    // Send welcome WhatsApp
    const name = storeObj?.owner_name || 'Store Owner';
    const welcomeMsg = `🎉 *Welcome to Grahbook, ${name}!* 🎉\n\nYour store has been activated. You can now:\n✅ Manage customers & bills via WhatsApp\n📊 Track sales & reports\n🧾 Generate GST invoices\n\nReply "help" anytime to see all commands.`;
    await sendWhatsAppMessage(np, welcomeMsg).catch(() => {});

    console.log(`✅ Store ${sid} activated with phone ${np}`);
    res.json({ success: true, message: 'Store activated successfully', store: storeObj || null });
  } catch (error) {
    console.error('Error activating store:', error);
    res.status(500).json({ success: false, message: 'Failed to activate store' });
  }
});

app.post('/api/register-store', async (req, res) => {
  try {
    const { store_name, owner_name, phone, email, business_type, plan, address, password, gstin, upi_id } = req.body;

    if (!store_name || !owner_name || !phone) {
      return res.status(400).json({ success: false, message: 'store_name, owner_name, and phone are required' });
    }
    // Validate input lengths
    if (String(store_name).trim().length < 2 || String(store_name).trim().length > 100) {
      return res.status(400).json({ success: false, message: 'Store name must be 2-100 characters' });
    }
    if (String(owner_name).trim().length < 2 || String(owner_name).trim().length > 100) {
      return res.status(400).json({ success: false, message: 'Owner name must be 2-100 characters' });
    }
    const np = normalizePhone(phone);
    if (np.length < 10 || np.length > 15) {
      return res.status(400).json({ success: false, message: 'Invalid phone number' });
    }
    // Enforce stronger password policy
    if (password && password.length < 6) {
      return res.status(400).json({ success: false, message: 'Password must be at least 6 characters' });
    }

    // Hash password for login
    let passwordHash = null;
    if (password && password.length >= 4) {
      passwordHash = await bcrypt.hash(password, 10);
    }

    // Generate unique store ID
    const storeId = generateStoreId(store_name);
    
    // Read existing database
    const db = await readDB();
    
    // Initialize stores array if it doesn't exist
    if (!db.stores) {
      db.stores = [];
    }
    
    // Check if store already exists
    const normalizedPhone = normalizePhone(phone);
    let existingStore = db.stores.find(s => s.phone === normalizedPhone || (email && s.email === email));

    // Special case: Google-created store with empty phone but matching email
    if (!existingStore && email) {
      existingStore = db.stores.find(s => s.email === email && (!s.phone || s.phone === ''));
    }

    if (existingStore) {
      // Update existing store with phone and password
      const shouldUpdate = existingStore.phone !== normalizedPhone || !existingStore.phone;

      if (shouldUpdate) existingStore.phone = normalizedPhone;
      if (password && password.length >= 4) {
        const newHash = await bcrypt.hash(password, 10);
        const existingStaff = db.staff.find(s => (s.phone === normalizedPhone || s.email === email) && (s.store_id || 'default') === existingStore.id);
        if (existingStaff) {
          existingStaff.password_hash = newHash;
          existingStaff.phone = normalizedPhone;
          existingStaff.status = 'active';
        }
      }
      existingStore.status = 'active';
      if (shouldUpdate) await writeDB(db);

      return res.status(200).json({
        status: 'exists',
        store_id: existingStore.id,
        message: 'Store found — you can login now'
      });
    }
    
    // Create new store
    const newStore = {
      id: storeId,
      store_name: store_name,
      owner_name: owner_name,
      phone: normalizePhone(phone),
      email: email || '',
      business_type: business_type || 'retail',
      plan: plan || 'basic',
      address: address || '',
      gstin: gstin || '',
      upi_id: upi_id || '',
      created_at: new Date().toISOString(),
      status: 'active'
    };
    
    // Add store to database
    db.stores.push(newStore);

    // Ensure owner is authorized for this store (staff entry)
    if (!db.staff) db.staff = [];
    const ownerPhone = normalizePhone(phone);
    const existsStaff = db.staff.find(s => s.phone === ownerPhone && (s.store_id || 'default') === storeId);
    if (!existsStaff) {
      db.staff.push({
        id: genId('s'),
        name: owner_name,
        phone: ownerPhone,
        role: 'owner',
        store_id: storeId,
        password_hash: passwordHash,
        status: 'active',
        email: email || '',
        created_at: new Date().toISOString(),
      });
    }
    
    // Write updated database
    await writeDB(db);
    
    // Send welcome message via WhatsApp
    const welcomeMessage = `🎉 *Welcome to Grahbook!* 🎉\n\nYour store "${store_name}" has been successfully registered.\n\n📱 *Your Store Dashboard Link:* https://grahbook.com/dashboard/${storeId}\n\nUse this link to access your personalized dashboard.\n\nIf you have any questions, feel free to reach out to our support team.\n\nHappy selling! 🚀`;
    await sendWhatsAppMessage(ownerPhone, welcomeMessage);
    
    res.json({ 
      status: 'success', 
      store_id: storeId,
      message: 'Store registered successfully'
    });
    
  } catch (error) {
    console.error('Error registering store:', error);
    res.status(500).json({ success: false, message: 'Failed to register store' });
  }
});

// GET /api/store/:storeId - Get store information
app.get('/api/store/:storeId', async (req, res) => {
  try {
    const { storeId } = req.params;
    const db = await readDB();
    
    const store = db.stores?.find(s => s.id === storeId);
    
    if (!store) {
      return res.status(404).json({ success: false, message: 'Store not found' });
    }
    
    res.json({ success: true, store });
  } catch (error) {
    console.error('Error fetching store:', error);
    res.status(500).json({ success: false, message: 'Failed to fetch store' });
  }
});

// POST /api/store/update - Update store details (needs bearer token)
app.post('/api/store/update', sessionAuthMiddleware, async (req, res) => {
  try {
    const { store_name, owner_name, phone, address, upi_id, gstin, invoice_template, gst_enabled, state } = req.body;
    const db = await readDB();
    const store = (db.stores || []).find(s => s.id === req.storeId);
    
    if (!store) {
      return res.status(404).json({ success: false, message: 'Store not found' });
    }

    if (store_name) store.store_name = store_name;
    if (owner_name) store.owner_name = owner_name;
    if (phone) store.phone = normalizePhone(phone);
    if (address !== undefined) store.address = address;
    if (upi_id !== undefined) store.upi_id = upi_id;
    if (gstin !== undefined) store.gstin = gstin;
    if (invoice_template !== undefined) store.invoice_template = invoice_template;
    if (gst_enabled !== undefined) store.gst_enabled = gst_enabled;
    if (state !== undefined) store.state = state;

    await writeDB(db);
    res.json({ success: true, message: 'Store details updated successfully' });
  } catch (error) {
    console.error('Error updating store details:', error);
    res.status(500).json({ success: false, message: 'Failed to update store details' });
  }
});

// POST /api/staff/add - Add a new staff member (MongoDB mode)
app.post('/api/staff/add', async (req, res) => {
  try {
    const { name, phone, role } = req.body;
    if (!name || !phone) {
      return res.status(400).json({ success: false, message: 'Name and phone are required' });
    }

    const np = normalizePhone(phone);
    const sid = req.storeId;

    const database = await connectDB();
    if (!database) {
      return res.status(500).json({ success: false, message: 'Database not available' });
    }

    // Check for duplicate phone within the same store
    const existing = await database.collection('staff').findOne({ phone: np, store_id: sid });
    if (existing) {
      return res.status(400).json({ success: false, message: 'A staff member with this phone already exists in your store' });
    }

    const newStaff = {
      id: genId('s'),
      name: name.trim(),
      phone: np,
      role: role || 'staff',
      store_id: sid,
      status: 'active',
    };

    await database.collection('staff').insertOne(newStaff);

    res.json({ success: true, staff: newStaff });
  } catch (error) {
    console.error('Error adding staff:', error);
    res.status(500).json({ success: false, message: 'Failed to add staff member' });
  }
});

// DELETE /api/staff/:id - Remove a staff member (MongoDB mode)
app.delete('/api/staff/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const sid = req.storeId;

    const database = await connectDB();
    if (!database) {
      return res.status(500).json({ success: false, message: 'Database not available' });
    }

    // Find the staff member by ID and store
    const staff = await database.collection('staff').findOne({ id, store_id: sid });
    if (!staff) {
      return res.status(404).json({ success: false, message: 'Staff member not found' });
    }

    // Prevent deleting yourself
    if (normalizePhone(staff.phone) === normalizePhone(req.staffPhone)) {
      return res.status(400).json({ success: false, message: 'You cannot remove yourself' });
    }

    await database.collection('staff').deleteOne({ id, store_id: sid });

    res.json({ success: true, message: 'Staff member removed successfully' });
  } catch (error) {
    console.error('Error removing staff:', error);
    res.status(500).json({ success: false, message: 'Failed to remove staff member' });
  }
});

// DELETE /api/transaction/:id - Delete a ledger transaction
app.delete('/api/transaction/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const sid = req.storeId || 'default';
    const fullDb = await readDB();
    if (!fullDb.transactions) fullDb.transactions = [];
    const idx = fullDb.transactions.findIndex(t => t.id === id && (t.store_id || 'default') === sid);
    if (idx === -1) {
      return res.status(404).json({ success: false, message: 'Transaction not found' });
    }
    fullDb.transactions.splice(idx, 1);
    await writeDB(fullDb);
    cachedDB = null;
    dbCacheTimestamp = 0;
    return res.json({ success: true, message: 'Transaction deleted successfully' });
  } catch (error) {
    console.error('Error deleting transaction:', error);
    return res.status(500).json({ success: false, message: 'Failed to delete transaction' });
  }
});

// DELETE /api/bill/:id - Delete a bill
app.delete('/api/bill/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const sid = req.storeId || 'default';
    const fullDb = await readDB();
    if (!fullDb.bills) fullDb.bills = [];
    const idx = fullDb.bills.findIndex(b => b.id === id && (b.store_id || 'default') === sid);
    if (idx === -1) {
      return res.status(404).json({ success: false, message: 'Bill not found' });
    }
    fullDb.bills.splice(idx, 1);
    await writeDB(fullDb);
    cachedDB = null;
    dbCacheTimestamp = 0;
    return res.json({ success: true, message: 'Bill deleted successfully' });
  } catch (error) {
    console.error('Error deleting bill:', error);
    return res.status(500).json({ success: false, message: 'Failed to delete bill' });
  }
});

// Helper function to generate unique store ID
function generateStoreId(storeName) {
  const cleanName = storeName.toLowerCase().replace(/[^a-z0-9]/g, '');
  const randomString = Math.random().toString(36).substring(2, 8);
  return `${cleanName}-${randomString}`;
}

// ─── MANUAL TRIGGER ROUTES ─────────────────────────────────────────────────────

// POST /api/send-reminders — Manually trigger payment reminders
app.post('/api/send-reminders', async (req, res) => {
  const db = await readDB();
  let sentCount = 0;
  const results = [];
  const baseUrl = getPublicBaseUrl(req);
  for (const c of db.customers) {
    const sid = c.store_id || 'default';
    const storeTrans = (db.transactions || []).filter(t => (t.store_id || 'default') === sid);
    const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === sid);
    const bal = getCustomerOutstanding(c.id, storeTrans, storeBills);
    if (bal > 0 && c.phone) {
      const store = (db.stores || []).find(s => s.id === sid);
      const shop = store
        ? {
            name: store.store_name,
            upi_id: store.upi_id || 'sharmakhata@upi'
          }
        : {
            name: db.shop?.name || 'General Store',
            upi_id: db.shop?.upi_id || 'sharmakhata@upi'
          };
      const cleanShopName = (shop.name || 'Store').replace(/[^a-zA-Z0-9 ]/g, '').trim();
      const viewUrl = `${baseUrl}/view/customer/${encodeURIComponent(c.id)}/statement`;
      const upiLink = `upi://pay?pa=${encodeURIComponent(shop.upi_id)}&pn=${encodeURIComponent(cleanShopName)}&am=${bal.toFixed(2)}&cu=INR&tn=Reminder`;

      const msg =
        `🙏 *${shop.name}*\n\n` +
        `Namaste *${c.name}* ji,\n\n` +
        `Aapka *${fmtRs(bal)}* ka baaki hai hamare yahan.\n` +
        `Kripya jald hi chukta karein.\n\n` +
        `📊 *View Statement & Pay:* ${viewUrl}\n` +
        `📱 *Direct UPI Payment:* ${upiLink}\n\n` +
        `Shukriya 🙏`;
      const sent = await sendWhatsAppMessage(c.phone, msg);
      if (sent) { sentCount++; results.push({ name: c.name, amount: bal }); }
    }
  }
  res.json({ success: true, sent: sentCount, results });
});

// GET /api/report — Get today's report JSON
app.get('/api/report', async (req, res) => {
  const db = await readStoreDB(req.storeId);
  const todayString = new Date().toISOString().substring(0, 10);
  const billsToday = db.bills.filter(b => b.created_at.startsWith(todayString));
  const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
  const collectionsToday = db.transactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(todayString));
  const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);
  const outstanding = db.customers.map(c => ({
    name: c.name, phone: c.phone,
    balance: getCustomerOutstanding(c.id, db.transactions, db.bills)
  })).filter(c => c.balance > 0);
  res.json({ date: todayString, billsTotal, paymentTotal, billsCount: billsToday.length, outstanding });
});

// ─── MOBILE → WHATSAPP ACTIONS (requires X-API-KEY) ───────────────────────────

// POST /api/whatsapp/send-invoice — Sends an invoice PDF to the customer via WhatsApp
app.post('/api/whatsapp/send-invoice', async (req, res) => {
  try {
    const { billId } = req.body || {};
    if (!billId) return res.status(400).json({ success: false, message: 'billId is required' });

    const db = await readStoreDB(req.storeId);
    const bill = db.bills.find(b => b.id === billId);
    if (!bill) return res.status(404).json({ success: false, message: 'Bill not found' });

    const customer = db.customers.find(c => c.id === bill.customer_id);
    if (!customer?.phone) return res.status(400).json({ success: false, message: 'Customer phone missing' });

    const shop = db.shop || {};
    const baseUrl = getPublicBaseUrl(req);
    const pdfUrl = `${baseUrl}/api/bill/${encodeURIComponent(billId)}/pdf?token=${generatePdfToken('bill', billId, req.storeId)}`;
    const viewUrl = `${baseUrl}/view/bill/${encodeURIComponent(billId)}`;
    const ok = await sendWhatsAppDocument(
      customer.phone,
      pdfUrl,
      `invoice-${billId}.pdf`,
      `🧾 Invoice from ${shop.name || 'Store'}\n\nView & Pay Online: ${viewUrl}`
    );

    return res.json({ success: ok, billId, customerPhone: customer.phone });
  } catch (error) {
    console.error('Error sending invoice:', error);
    return res.status(500).json({ success: false, message: 'Failed to send invoice' });
  }
});

// POST /api/whatsapp/send-statement — Sends customer statement PDF via WhatsApp
app.post('/api/whatsapp/send-statement', async (req, res) => {
  try {
    const { customerId } = req.body || {};
    if (!customerId) return res.status(400).json({ success: false, message: 'customerId is required' });

    const db = await readStoreDB(req.storeId);
    const customer = db.customers.find(c => c.id === customerId);
    if (!customer) return res.status(404).json({ success: false, message: 'Customer not found' });
    if (!customer.phone) return res.status(400).json({ success: false, message: 'Customer phone missing' });

    const shop = db.shop || {};
    const baseUrl = getPublicBaseUrl(req);
    const pdfUrl = `${baseUrl}/api/customer/${encodeURIComponent(customerId)}/statement/pdf?token=${generatePdfToken('statement', customerId, req.storeId)}`;
    const viewUrl = `${baseUrl}/view/customer/${encodeURIComponent(customerId)}/statement`;
    const ok = await sendWhatsAppDocument(
      customer.phone,
      pdfUrl,
      `statement-${customerId}.pdf`,
      `📊 Account statement from ${shop.name || 'Store'}\n\nView Online: ${viewUrl}`
    );

    return res.json({ success: ok, customerId, customerPhone: customer.phone });
  } catch (error) {
    console.error('Error sending statement:', error);
    return res.status(500).json({ success: false, message: 'Failed to send statement' });
  }
});

// POST /api/whatsapp/send-reminder — Sends an outstanding reminder via WhatsApp
app.post('/api/whatsapp/send-reminder', async (req, res) => {
  try {
    const { customerId, message } = req.body || {};
    if (!customerId) return res.status(400).json({ success: false, message: 'customerId is required' });

    const db = await readStoreDB(req.storeId);
    const customer = db.customers.find(c => c.id === customerId);
    if (!customer) return res.status(404).json({ success: false, message: 'Customer not found' });
    if (!customer.phone) return res.status(400).json({ success: false, message: 'Customer phone missing' });

    const shop = db.shop || {};
    const bal = getCustomerOutstanding(customerId, db.transactions, db.bills);
    const baseUrl = getPublicBaseUrl(req);
    const viewUrl = `${baseUrl}/view/customer/${encodeURIComponent(customerId)}/statement`;
    const upiId = shop.upi_id || 'sharmakhata@upi';
    const cleanShopName = (shop.name || 'Store').replace(/[^a-zA-Z0-9 ]/g, '').trim();
    const upiLink = `upi://pay?pa=${encodeURIComponent(upiId)}&pn=${encodeURIComponent(cleanShopName)}&am=${bal.toFixed(2)}&cu=INR&tn=Reminder`;

    const text =
      (message && String(message).trim()) ||
      `🙏 *${shop.name || 'Store'}*\n\nNamaste *${customer.name}* ji,\n\nAapka *${fmtRs(bal)}* ka baaki (outstanding) hai.\nKripya jald hi chukta karein.\n\n📊 *View Statement & Pay:* ${viewUrl}\n📱 *Direct UPI Payment:* ${upiLink}\n\nShukriya 🙏`;

    const ok = await sendWhatsAppMessage(customer.phone, text);
    return res.json({ success: ok, customerId, customerPhone: customer.phone, outstanding: bal });
  } catch (error) {
    console.error('Error sending reminder:', error);
    return res.status(500).json({ success: false, message: 'Failed to send reminder' });
  }
});

// GET /api/bill/:id/pdf — Generate and return PDF invoice
app.get('/api/bill/:id/pdf', pdfAuthMiddleware, async (req, res) => {
  try {
    const db = await readDB();
    const bill = db.bills.find(b => b.id === req.params.id);

    if (!bill) {
      return res.status(404).json({ success: false, message: 'Bill not found' });
    }

    const sid = bill.store_id || 'default';
    const store = (db.stores || []).find(s => s.id === sid);
    const shop = store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          store_id: sid,
        }
      : (sid === 'default' && db.shop ? db.shop : {});

    const customer = db.customers.find(c => c.id === bill.customer_id) || {
      name: 'Walk-in Customer',
      phone: '0000000000'
    };

    // Colors dynamic based on invoice template style
    const template = (shop.invoice_template || 'modern').toLowerCase();
    let PRIMARY_COLOR = '#10B981'; // Modern (Emerald)
    let PRIMARY_DARK = '#065F46';
    let PRIMARY_LIGHT = '#ECFDF5';
    let SECONDARY_TEXT = '#A7F3D0';

    if (template === 'classic') {
      PRIMARY_COLOR = '#4F46E5'; // Classic (Indigo)
      PRIMARY_DARK = '#312E81';
      PRIMARY_LIGHT = '#EEF2FF';
      SECONDARY_TEXT = '#C7D2FE';
    } else if (template === 'professional') {
      PRIMARY_COLOR = '#0F172A'; // Professional (Slate Slate-900)
      PRIMARY_DARK = '#020617';
      PRIMARY_LIGHT = '#F1F5F9';
      SECONDARY_TEXT = '#94A3B8';
    } else if (template === 'minimal') {
      PRIMARY_COLOR = '#374151'; // Minimal (Charcoal Grey-700)
      PRIMARY_DARK = '#111827';
      PRIMARY_LIGHT = '#F9FAFB';
      SECONDARY_TEXT = '#9CA3AF';
    }

    const INDIGO = PRIMARY_COLOR;
    const INDIGO_DARK = PRIMARY_DARK;
    const INDIGO_LIGHT = PRIMARY_LIGHT;
    const SLATE_50 = '#F8FAFC';
    const SLATE_100 = '#F1F5F9';
    const SLATE_200 = '#E2E8F0';
    const SLATE_400 = '#94A3B8';
    const SLATE_600 = '#475569';
    const SLATE_800 = '#1E293B';
    const SLATE_900 = '#0F172A';
    const GREEN = '#10B981';
    const RED = '#EF4444';

    let qrBuffer = null;
    const finalTotal = bill.gst_rate > 0 ? (bill.grand_total || bill.total) : bill.total;
    if (store && store.upi_id && bill.status !== 'paid') {
      try {
        const upiUrl = `upi://pay?pa=${encodeURIComponent(store.upi_id)}&pn=${encodeURIComponent(shop.name || 'Store')}&am=${finalTotal.toFixed(2)}&cu=INR`;
        const qrUrl = `https://quickchart.io/qr?text=${encodeURIComponent(upiUrl)}&size=100&margin=0`;
        const qrResponse = await axios.get(qrUrl, { responseType: 'arraybuffer' });
        qrBuffer = Buffer.from(qrResponse.data, 'binary');
      } catch (e) {
        console.error("Failed to generate QR code:", e.message);
      }
    }

    const doc = new PDFDocument({ size: 'A4', margin: 0 });

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=invoice-${bill.id}.pdf`);
    await new Promise((resolve, reject) => {
      doc.on('finish', resolve);
      doc.on('error', reject);
      doc.pipe(res);

    const pageWidth = 595; // A4 width
    const margin = 40;
    const contentWidth = pageWidth - (margin * 2);

    // ── HEADER BANNER ────────────────────────────────────────
    const headerHeight = shop.gstin ? 125 : 110;
    doc.rect(0, 0, pageWidth, headerHeight).fill(INDIGO);
    doc.fontSize(22).fillColor('#FFFFFF')
       .text(shop.name || 'GENERAL STORE', margin, 20, { width: contentWidth, align: 'center' });
    doc.fontSize(9).fillColor(SECONDARY_TEXT)
       .text(shop.address || '', margin, 48, { width: contentWidth, align: 'center' });
    
    let subHeaderY = 62;
    if (shop.phone) {
      doc.fontSize(9).fillColor(SECONDARY_TEXT)
         .text(`WhatsApp: +91 ${shop.phone}`, margin, subHeaderY, { width: contentWidth, align: 'center' });
      subHeaderY += 13;
    }
    if (shop.gstin) {
      doc.fontSize(9).fillColor(SECONDARY_TEXT)
         .text(`GSTIN: ${shop.gstin}`, margin, subHeaderY, { width: contentWidth, align: 'center' });
      subHeaderY += 13;
    }
    doc.fontSize(9).fillColor(SECONDARY_TEXT)
       .text('TAX INVOICE', margin, subHeaderY, { width: contentWidth, align: 'center' });

    // ── INVOICE META ─────────────────────────
    let y = headerHeight + 20;
    doc.fontSize(10).fillColor(SLATE_400).text('Invoice #', margin, y);
    doc.fontSize(11).fillColor(SLATE_900).text(bill.id, margin, y + 14);

    doc.fontSize(10).fillColor(SLATE_400).text('Date', margin + 200, y);
    doc.fontSize(11).fillColor(SLATE_900).text(new Date(bill.created_at).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }), margin + 200, y + 14);

    const statusLabel = bill.status === 'paid' ? 'PAID' : 'UNPAID';
    const statusBg = bill.status === 'paid' ? GREEN : RED;
    doc.roundedRect(margin + 400, y, 80, 22, 4).fill(statusBg);
    doc.fontSize(9).fillColor('#FFFFFF')
       .text(statusLabel, margin + 400, y + 6, { width: 80, align: 'center' });

    y += 40;

    // ── BILL TO ──────────────────────────────────────────────
    doc.roundedRect(margin, y, contentWidth, 55, 6).fill(INDIGO_LIGHT);
    doc.fontSize(8).fillColor(INDIGO).text('BILL TO', margin + 12, y + 8);
    doc.fontSize(13).fillColor(SLATE_900).text(customer.name, margin + 12, y + 22, { continued: false });
    doc.fontSize(10).fillColor(SLATE_600).text(`+91 ${customer.phone}`, margin + 12, y + 38);

    y += 70;

    // ── ITEMS TABLE HEADER ───────────────────────────────────
    const colX = { sno: margin, desc: margin + 35, qty: margin + 280, price: margin + 350, total: margin + 435 };
    const colW = { sno: 35, desc: 245, qty: 70, price: 85, total: 80 };

    doc.roundedRect(margin, y, contentWidth, 28, 4).fill(INDIGO);
    doc.fontSize(9).fillColor('#FFFFFF');
    doc.text('#', colX.sno + 8, y + 9, { width: colW.sno, align: 'center' });
    doc.text('Item', colX.desc, y + 9, { width: colW.desc, align: 'left' });
    doc.text('Qty', colX.qty, y + 9, { width: colW.qty, align: 'center' });
    doc.text('Price', colX.price, y + 9, { width: colW.price, align: 'right' });
    doc.text('Amount', colX.total, y + 9, { width: colW.total, align: 'right' });

    y += 32;

    // ── ITEMS TABLE ROWS ─────────────────────────────────────
    bill.items.forEach((item, index) => {
      const itemTotal = item.price * item.qty;
      const rowBg = index % 2 === 0 ? '#FFFFFF' : SLATE_50;

      // Row background
      doc.rect(margin, y, contentWidth, 26).fill(rowBg);

      doc.fontSize(9).fillColor(SLATE_400).text(`${index + 1}`, colX.sno + 8, y + 8, { width: colW.sno, align: 'center' });
      doc.fontSize(10).fillColor(SLATE_800).text(item.name, colX.desc, y + 8, { width: colW.desc, align: 'left' });
      doc.fontSize(10).fillColor(SLATE_600).text(`${item.qty}`, colX.qty, y + 8, { width: colW.qty, align: 'center' });
      doc.fontSize(10).fillColor(SLATE_600).text(`₹${item.price.toLocaleString('en-IN')}`, colX.price, y + 8, { width: colW.price, align: 'right' });
      doc.fontSize(10).fillColor(SLATE_900).text(`₹${itemTotal.toLocaleString('en-IN')}`, colX.total, y + 8, { width: colW.total, align: 'right' });

      y += 26;
    });

    // Bottom border
    doc.strokeColor(SLATE_200).lineWidth(0.5).moveTo(margin, y).lineTo(margin + contentWidth, y).stroke();
    y += 15;

    // ── TOTALS SECTION ───────────────────────────────────────
    const totalsX = margin + 300;
    const totalsW = 215;
    const isGst = bill.gst_rate > 0;
    const finalTotal = isGst ? (bill.grand_total || bill.total) : bill.total;

    if (isGst) {
      // Subtotal (Taxable Value)
      doc.fontSize(10).fillColor(SLATE_600).text('Taxable Value', totalsX, y, { width: 120, align: 'left' });
      doc.fontSize(10).fillColor(SLATE_800).text(`₹${(bill.taxable_amount || bill.total).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
      y += 18;

      if (bill.gst_type === 'cgst_sgst') {
        const halfRate = bill.gst_rate / 2;
        doc.fontSize(9).fillColor(SLATE_500).text(`CGST (${halfRate}%)`, totalsX, y, { width: 120, align: 'left' });
        doc.fontSize(9).fillColor(SLATE_700).text(`₹${(bill.total_cgst || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
        y += 16;
        
        doc.fontSize(9).fillColor(SLATE_500).text(`SGST (${halfRate}%)`, totalsX, y, { width: 120, align: 'left' });
        doc.fontSize(9).fillColor(SLATE_700).text(`₹${(bill.total_sgst || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
        y += 16;
      } else if (bill.gst_type === 'igst') {
        doc.fontSize(9).fillColor(SLATE_500).text(`IGST (${bill.gst_rate}%)`, totalsX, y, { width: 120, align: 'left' });
        doc.fontSize(9).fillColor(SLATE_700).text(`₹${(bill.total_igst || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
        y += 16;
      }
    } else {
      // Subtotal
      doc.fontSize(10).fillColor(SLATE_600).text('Subtotal', totalsX, y, { width: 120, align: 'left' });
      doc.fontSize(10).fillColor(SLATE_800).text(`₹${bill.total.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
      y += 22;
      }

      if (bill.discount && bill.discount > 0) {
        doc.fontSize(10).fillColor(SLATE_600).text('Discount', totalsX, y, { width: 120, align: 'left' });
        doc.fontSize(10).fillColor(RED).text(`-₹${bill.discount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
        y += 22;
      }

      // Grand Total box
    doc.roundedRect(totalsX - 10, y, totalsW + 20, 36, 6).fill(INDIGO_LIGHT);
    doc.fontSize(12).fillColor(INDIGO_DARK).text('Grand Total', totalsX, y + 10, { width: 120, align: 'left' });
    doc.fontSize(16).fillColor(INDIGO).text(`₹${finalTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, totalsX + 100, y + 8, { width: 115, align: 'right' });

    y += 55;

    // ── OUTSTANDING ──────────────────────────────────────────
    const outstanding = getCustomerOutstanding(customer.id, db.transactions || [], db.bills || []);
    if (outstanding !== finalTotal || bill.status === 'paid') {
      doc.fontSize(9).fillColor(SLATE_400).text('Total Outstanding (incl. previous):', margin, y);
      const outColor = outstanding > 0 ? RED : GREEN;
      doc.fontSize(11).fillColor(outColor).text(`₹${Math.abs(outstanding).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, margin, y + 14);
      y += 35;
    }

    if (qrBuffer) {
      doc.image(qrBuffer, margin, y, { width: 60 });
      doc.fontSize(9).fillColor(SLATE_800).text('Scan to Pay', margin + 70, y + 20);
      doc.fontSize(8).fillColor(SLATE_500).text(`UPI: ${store.upi_id}`, margin + 70, y + 32);
      y += 65;
    }

    // ── FOOTER ───────────────────────────────────────────────
    const footerY = 760;
    doc.rect(0, footerY, pageWidth, 82).fill(SLATE_100);
    doc.fontSize(10).fillColor(SLATE_600)
       .text('Thank you for your purchase!', margin, footerY + 15, { width: contentWidth, align: 'center' });
    doc.fontSize(8).fillColor(SLATE_400)
       .text('This is a computer-generated invoice. No signature required.', margin, footerY + 32, { width: contentWidth, align: 'center' });
    doc.text(`Generated by ${(shop.name || 'Grahbook Pro')} — Digital Khata & Billing`, margin, footerY + 45, { width: contentWidth, align: 'center' });
    doc.text(`Powered by Grahbook`, margin, footerY + 58, { width: contentWidth, align: 'center' });

    doc.end();
    });

  } catch (error) {
    console.error('Error generating PDF:', error);
    doc.destroy();
    if (!res.headersSent) {
      res.removeHeader('Content-Disposition');
      res.removeHeader('Content-Type');
      res.status(500).json({ success: false, message: 'Failed to generate PDF' });
    }
  }
});

// GET /api/customer/:id/statement/pdf — Generate customer statement PDF
app.get('/api/customer/:id/statement/pdf', pdfAuthMiddleware, async (req, res) => {
  try {
    const db = await readDB();
    const customer = db.customers.find(c => c.id === req.params.id);

    if (!customer) {
      return res.status(404).json({ success: false, message: 'Customer not found' });
    }

    const sid = customer.store_id || 'default';
    const store = (db.stores || []).find(s => s.id === sid);
    const shop = store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          store_id: sid,
        }
      : (sid === 'default' && db.shop ? db.shop : {});

    const customerTransactions = db.transactions.filter(t => t.customer_id === customer.id);
    const customerBills = db.bills.filter(b => b.customer_id === customer.id);
    const balance = getCustomerOutstanding(customer.id, db.transactions, db.bills);

    // Colors
    const INDIGO = '#4F46E5';
    const INDIGO_DARK = '#312E81';
    const INDIGO_LIGHT = '#EEF2FF';
    const SLATE_50 = '#F8FAFC';
    const SLATE_100 = '#F1F5F9';
    const SLATE_200 = '#E2E8F0';
    const SLATE_400 = '#94A3B8';
    const SLATE_600 = '#475569';
    const SLATE_800 = '#1E293B';
    const SLATE_900 = '#0F172A';
    const GREEN = '#10B981';
    const RED = '#EF4444';

    const doc = new PDFDocument({ size: 'A4', margin: 0 });
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=statement-${customer.name.replace(/\s+/g, '_')}.pdf`);
    await new Promise((resolve, reject) => {
      doc.on('finish', resolve);
      doc.on('error', reject);
      doc.pipe(res);

    const pageWidth = 595;
    const margin = 40;
    const contentWidth = pageWidth - (margin * 2);

    // ── HEADER BANNER ────────────────────────────────────────
    doc.rect(0, 0, pageWidth, 100).fill(INDIGO);
    doc.fontSize(22).fillColor('#FFFFFF')
       .text(shop.name || 'GENERAL STORE', margin, 22, { width: contentWidth, align: 'center' });
    doc.fontSize(10).fillColor('#C7D2FE')
       .text(shop.address || '', margin, 50, { width: contentWidth, align: 'center' });
    if (shop.phone) {
      doc.text(`WhatsApp: +91 ${shop.phone}`, margin, 64, { width: contentWidth, align: 'center' });
    }
    doc.fontSize(9).fillColor('#A5B4FC')
       .text('CUSTOMER ACCOUNT STATEMENT', margin, 82, { width: contentWidth, align: 'center' });

    let y = 118;

    // ── CUSTOMER INFO ────────────────────────────────────────
    doc.roundedRect(margin, y, contentWidth, 50, 6).fill(INDIGO_LIGHT);
    doc.fontSize(8).fillColor(INDIGO).text('CUSTOMER', margin + 12, y + 8);
    doc.fontSize(13).fillColor(SLATE_900).text(customer.name, margin + 12, y + 20);
    doc.fontSize(10).fillColor(SLATE_600).text(`+91 ${customer.phone}   |   ID: ${customer.id}`, margin + 12, y + 36);
    y += 62;

    // ── BALANCE SUMMARY CARDS ────────────────────────────────
    const cardW = (contentWidth - 10) / 3;
    const balColor = balance > 0 ? RED : (balance < 0 ? GREEN : SLATE_800);
    const balLabel = balance > 0 ? 'Amount Due' : (balance < 0 ? 'Advance' : 'Settled');

    // Card 1: Outstanding
    doc.roundedRect(margin, y, cardW, 55, 6).fill('#FFFFFF');
    doc.strokeColor(SLATE_200).roundedRect(margin, y, cardW, 55, 6).stroke();
    doc.fontSize(8).fillColor(SLATE_400).text('OUTSTANDING', margin + 10, y + 8);
    doc.fontSize(16).fillColor(balColor).text(fmtRs(balance), margin + 10, y + 24);
    doc.fontSize(8).fillColor(balColor).text(balLabel, margin + 10, y + 42);

    // Card 2: Transactions
    doc.roundedRect(margin + cardW + 5, y, cardW, 55, 6).fill('#FFFFFF');
    doc.strokeColor(SLATE_200).roundedRect(margin + cardW + 5, y, cardW, 55, 6).stroke();
    doc.fontSize(8).fillColor(SLATE_400).text('TRANSACTIONS', margin + cardW + 15, y + 8);
    doc.fontSize(16).fillColor(SLATE_800).text(`${customerTransactions.length}`, margin + cardW + 15, y + 24);
    doc.fontSize(8).fillColor(SLATE_400).text('total entries', margin + cardW + 15, y + 42);

    // Card 3: Bills
    doc.roundedRect(margin + (cardW + 5) * 2, y, cardW, 55, 6).fill('#FFFFFF');
    doc.strokeColor(SLATE_200).roundedRect(margin + (cardW + 5) * 2, y, cardW, 55, 6).stroke();
    doc.fontSize(8).fillColor(SLATE_400).text('BILLS', margin + (cardW + 5) * 2 + 10, y + 8);
    doc.fontSize(16).fillColor(SLATE_800).text(`${customerBills.length}`, margin + (cardW + 5) * 2 + 10, y + 24);
    doc.fontSize(8).fillColor(SLATE_400).text('total invoices', margin + (cardW + 5) * 2 + 10, y + 42);

    y += 68;

    // ── TRANSACTION HISTORY ──────────────────────────────────
    if (customerTransactions.length > 0) {
      doc.fontSize(11).fillColor(SLATE_900).text('Transaction History', margin, y);
      y += 18;

      // Table header
      doc.roundedRect(margin, y, contentWidth, 24, 4).fill(INDIGO);
      doc.fontSize(8).fillColor('#FFFFFF');
      doc.text('Date', margin + 10, y + 8, { width: 80, align: 'left' });
      doc.text('Type', margin + 100, y + 8, { width: 70, align: 'center' });
      doc.text('Amount', margin + 180, y + 8, { width: 80, align: 'right' });
      doc.text('Note', margin + 270, y + 8, { width: 240, align: 'left' });
      y += 28;

      customerTransactions.forEach((tx, index) => {
        const rowBg = index % 2 === 0 ? '#FFFFFF' : SLATE_50;
        doc.rect(margin, y, contentWidth, 22).fill(rowBg);

        const typeColor = tx.type === 'payment' ? GREEN : RED;
        doc.fontSize(9).fillColor(SLATE_600).text(fmtDate(tx.timestamp), margin + 10, y + 6, { width: 80, align: 'left' });
        doc.fillColor(typeColor).text(tx.type.toUpperCase(), margin + 100, y + 6, { width: 70, align: 'center' });
        doc.fillColor(SLATE_800).text(`₹${tx.amount.toLocaleString('en-IN')}`, margin + 180, y + 6, { width: 80, align: 'right' });
        doc.fillColor(SLATE_600).text((tx.note || '-').substring(0, 40), margin + 270, y + 6, { width: 240, align: 'left' });
        y += 22;
      });

      doc.strokeColor(SLATE_200).lineWidth(0.5).moveTo(margin, y).lineTo(margin + contentWidth, y).stroke();
      y += 15;
    }

    // ── BILL HISTORY ─────────────────────────────────────────
    if (customerBills.length > 0) {
      doc.fontSize(11).fillColor(SLATE_900).text('Bill History', margin, y);
      y += 18;

      doc.roundedRect(margin, y, contentWidth, 24, 4).fill(INDIGO);
      doc.fontSize(8).fillColor('#FFFFFF');
      doc.text('Bill #', margin + 10, y + 8, { width: 100, align: 'left' });
      doc.text('Date', margin + 120, y + 8, { width: 80, align: 'left' });
      doc.text('Status', margin + 260, y + 8, { width: 70, align: 'center' });
      doc.text('Total', margin + 400, y + 8, { width: 110, align: 'right' });
      y += 28;

      customerBills.forEach((bill, index) => {
        const rowBg = index % 2 === 0 ? '#FFFFFF' : SLATE_50;
        doc.rect(margin, y, contentWidth, 22).fill(rowBg);

        const statusColor = bill.status === 'paid' ? GREEN : RED;
        doc.fontSize(9).fillColor(SLATE_800).text(bill.id, margin + 10, y + 6, { width: 100, align: 'left' });
        doc.fillColor(SLATE_600).text(fmtDate(bill.created_at), margin + 120, y + 6, { width: 80, align: 'left' });
        doc.fillColor(statusColor).text(bill.status.toUpperCase(), margin + 260, y + 6, { width: 70, align: 'center' });
        doc.fillColor(SLATE_800).text(fmtRs(bill.total), margin + 400, y + 6, { width: 110, align: 'right' });
        y += 22;
      });
    }

    // ── FOOTER ───────────────────────────────────────────────
    const footerY = 760;
    doc.rect(0, footerY, pageWidth, 82).fill(SLATE_100);
    doc.fontSize(9).fillColor(SLATE_600)
       .text('This is a computer-generated account statement.', margin, footerY + 18, { width: contentWidth, align: 'center' });
    doc.fontSize(8).fillColor(SLATE_400)
       .text('For any discrepancies, please contact the store.', margin, footerY + 33, { width: contentWidth, align: 'center' });
    doc.text(`Generated: ${new Date().toLocaleString('en-IN')} | ${shop.name || 'Grahbook Pro'}`, margin, footerY + 48, { width: contentWidth, align: 'center' });
    doc.text('Powered by Grahbook', margin, footerY + 61, { width: contentWidth, align: 'center' });

    doc.end();
    });

  } catch (error) {
    console.error('Error generating statement PDF:', error);
    doc.destroy();
    if (!res.headersSent) {
      res.removeHeader('Content-Disposition');
      res.removeHeader('Content-Type');
      res.status(500).json({ success: false, message: 'Failed to generate statement PDF' });
    }
  }
});

// ─── PUBLIC GUEST WEB VIEWERS (No Auth Required) ────────────────────────────────

// GET /view/bill/:id — Public customer invoice viewer
app.get('/view/bill/:id', async (req, res) => {
  try {
    const db = await readDB();
    const bill = db.bills.find(b => b.id === req.params.id);
    if (!bill) {
      return res.status(404).send(`
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Invoice Not Found / इनवॉइस नहीं मिला</title>
          <script src="https://cdn.tailwindcss.com"></script>
          <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800&display=swap" rel="stylesheet">
          <style>body { font-family: 'Outfit', sans-serif; }</style>
        </head>
        <body class="bg-slate-50 flex items-center justify-center min-h-screen p-4 text-center">
          <div class="max-w-md bg-white rounded-3xl p-8 shadow-xl border border-slate-100 animate-scale-in">
            <div class="h-16 w-16 bg-red-50 text-red-500 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>
            </div>
            <h1 class="text-2xl font-extrabold text-slate-800 mb-2">Invoice Not Found</h1>
            <p class="text-slate-500 mb-6">Aapka manga hua invoice system me nahi mila. Kripya dukan se sampark karein.</p>
            <a href="https://grahbook.com" class="inline-flex items-center justify-center px-6 py-3 bg-indigo-600 text-white rounded-full font-bold shadow-md hover:bg-indigo-700 transition-colors">Go to Grahbook</a>
          </div>
        </body>
        </html>
      `);
    }

    const sid = bill.store_id || 'default';
    const store = (db.stores || []).find(s => s.id === sid);
    const shop = store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          upi_id: store.upi_id || 'sharmakhata@upi',
          gstin: store.gstin || ''
        }
      : {
          name: db.shop?.name || 'General Store',
          owner: db.shop?.owner || '',
          phone: db.shop?.phone || '',
          address: db.shop?.address || '',
          upi_id: db.shop?.upi_id || 'sharmakhata@upi',
          gstin: db.shop?.gstin || ''
        };

    const customer = db.customers.find(c => c.id === bill.customer_id) || {
      name: 'Walk-in Customer',
      phone: '0000000000'
    };

    const isPaid = bill.status === 'paid';
    const grandTotal = bill.grand_total || bill.total;
    const template = (shop.invoice_template || 'modern').toLowerCase();
    let bgGradient = 'from-emerald-600 to-emerald-800'; // Modern
    let textAccent = 'text-emerald-600';
    if (template === 'classic') {
      bgGradient = 'from-indigo-600 to-indigo-800'; // Classic
      textAccent = 'text-indigo-600';
    } else if (template === 'professional') {
      bgGradient = 'from-slate-800 to-slate-950'; // Professional
      textAccent = 'text-slate-900';
    } else if (template === 'minimal') {
      bgGradient = 'from-gray-700 to-gray-800'; // Minimal
      textAccent = 'text-gray-700';
    }
    const cleanShopName = (shop.name || 'Store').replace(/[^a-zA-Z0-9 ]/g, '').trim();
    const upiUri = `upi://pay?pa=${encodeURIComponent(shop.upi_id)}&pn=${encodeURIComponent(cleanShopName)}&am=${grandTotal.toFixed(2)}&cu=INR&tn=${encodeURIComponent('Bill ' + bill.id.substring(0,8))}`;

    const html = `
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Invoice #${escapeHtml(bill.id.substring(0, 8).toUpperCase())} — ${escapeHtml(shop.name)}</title>
        <script src="https://cdn.tailwindcss.com"></script>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <style>
          body { font-family: 'Outfit', sans-serif; }
          .glass-card {
            background: rgba(255, 255, 255, 0.85);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
          }
        </style>
      </head>
      <body class="bg-gradient-to-tr from-slate-50 to-slate-100 min-h-screen text-slate-800 pb-20">
        <div class="max-w-xl mx-auto px-4 pt-6">
          
          <!-- Back header / Status -->
          <div class="flex items-center justify-between mb-6">
            <span class="text-xs font-bold uppercase tracking-wider text-slate-400">Invoice Viewer</span>
            <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold ${
              isPaid
                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                : 'bg-rose-50 text-rose-700 border border-rose-200 animate-pulse'
            }">
              <span class="h-2 w-2 rounded-full ${isPaid ? 'bg-emerald-500' : 'bg-rose-500'}"></span>
              ${isPaid ? 'Paid / भुगतान हो गया' : 'Unpaid / बकाया है'}
            </span>
          </div>

          <!-- Main Card -->
          <div class="bg-white rounded-3xl shadow-xl border border-slate-100 overflow-hidden mb-6">
            
            <!-- Store Profile Header -->
            <div class="bg-gradient-to-r ${bgGradient} p-6 text-white text-center sm:text-left">
              <h2 class="text-2xl font-extrabold tracking-tight">${escapeHtml(shop.name)}</h2>
              ${shop.owner ? `<p class="text-indigo-200 text-sm mt-0.5">Prop: ${escapeHtml(shop.owner)}</p>` : ''}
              <div class="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-xs text-indigo-100 border-t border-indigo-500/30 pt-3">
                ${shop.phone ? `<span class="flex items-center justify-center sm:justify-start gap-1">📞 ${escapeHtml(shop.phone)}</span>` : ''}
                ${shop.address ? `<span class="flex items-center justify-center sm:justify-start gap-1">📍 ${escapeHtml(shop.address)}</span>` : ''}
                ${shop.gstin ? `<span class="flex items-center justify-center sm:justify-start gap-1">🧾 GSTIN: ${escapeHtml(shop.gstin)}</span>` : ''}
              </div>
            </div>

            <!-- Details Block -->
            <div class="p-6 border-b border-slate-100">
              <div class="flex justify-between items-start mb-6">
                <div>
                  <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Bill To / ग्राहक</p>
                  <h3 class="text-base font-extrabold text-slate-800 mt-1">${escapeHtml(customer.name)}</h3>
                  ${customer.phone ? `<p class="text-xs text-slate-500 mt-0.5">📞 ${escapeHtml(customer.phone)}</p>` : ''}
                </div>
                <div class="text-right">
                  <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Invoice Date / तारीख</p>
                  <p class="text-sm font-semibold text-slate-800 mt-1">${new Date(bill.created_at).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</p>
                  <p class="text-[10px] font-mono text-slate-400 mt-1 uppercase">#${bill.id.substring(0,12)}</p>
                </div>
              </div>

              <!-- Items Table -->
              <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-3">Items list / सामान सूची</p>
              <div class="space-y-2.5">
                ${bill.items.map(item => `
                  <div class="flex justify-between items-center bg-slate-50/70 p-3 rounded-2xl border border-slate-100 hover:bg-slate-50 transition-colors">
                    <div class="min-w-0 pr-2">
                      <p class="text-sm font-bold text-slate-800 truncate">${escapeHtml(item.name)}</p>
                      <p class="text-[11px] text-slate-400 font-medium">
                        ${item.qty} × ₹${item.price.toFixed(2)}
                        ${item.hsn_code ? ` | HSN: ${item.hsn_code}` : ''}
                        ${item.gst_rate > 0 ? ` | GST: ${item.gst_rate}%` : ''}
                      </p>
                    </div>
                    <span class="text-sm font-extrabold text-slate-800">₹${(item.qty * item.price).toFixed(2)}</span>
                  </div>
                `).join('')}
              </div>
            </div>

            <!-- Totals block -->
            <div class="p-6 bg-slate-50/50">
              <div class="space-y-2 text-sm text-slate-600">
                <div class="flex justify-between">
                  <span>Subtotal / कुल सामान मूल्य</span>
                  <span class="font-semibold">₹${bill.total.toFixed(2)}</span>
                </div>
                
                ${bill.gst_rate > 0 ? `
                  <div class="flex justify-between text-xs text-slate-400">
                    <span>Taxable Amount</span>
                    <span>₹${(bill.taxable_amount || bill.total).toFixed(2)}</span>
                  </div>
                  <div class="flex justify-between text-xs text-slate-400">
                    <span>GST (${bill.gst_rate}%)</span>
                    <span>₹${((bill.total_cgst || 0) + (bill.total_sgst || 0) + (bill.total_igst || 0)).toFixed(2)}</span>
                  </div>
                ` : ''}
                
                <div class="flex justify-between items-end pt-3 border-t border-slate-200/60 text-slate-900">
                  <span class="font-bold">Total Amount Due / कुल देय राशि</span>
                  <span class="text-2xl font-extrabold ${textAccent}">₹${grandTotal.toFixed(2)}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Interactive UPI Payment Section -->
          ${!isPaid ? `
            <div class="bg-gradient-to-br from-amber-50 to-orange-50 rounded-3xl border border-amber-200/60 p-6 shadow-md mb-6 text-center animate-scale-in">
              <h3 class="text-lg font-bold text-amber-900 mb-1 flex items-center justify-center gap-1.5">
                ⚡ Instant Payment / तुरंत भुगतान
              </h3>
              <p class="text-xs text-amber-700/80 mb-5">Click below to pay via GPay, PhonePe, Paytm, or scan QR code</p>

              <!-- Mobile Pay Now Button -->
              <a href="${upiUri}" class="w-full flex items-center justify-center gap-2 py-4 bg-emerald-600 text-white font-extrabold rounded-2xl shadow-lg hover:bg-emerald-700 hover:shadow-emerald-200 active:scale-95 transition-all text-base mb-6">
                <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
                PAY NOW VIA UPI (₹${grandTotal.toFixed(2)})
              </a>

              <!-- Desktop QR code -->
              <div class="hidden sm:block border-t border-amber-200/60 pt-5">
                <div class="bg-white p-4 inline-block rounded-2xl shadow-inner border border-amber-100 mb-2">
                  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(upiUri)}" alt="UPI QR Code" class="h-36 w-36">
                </div>
                <p class="text-[10px] text-amber-600 font-bold tracking-wide uppercase">Scan with GPay/PhonePe/Paytm</p>
              </div>
            </div>
          ` : ''}

          <!-- PDF & Action Footer -->
          <div class="flex gap-3">
            <a href="/api/bill/${encodeURIComponent(bill.id)}/pdf" class="flex-1 flex items-center justify-center gap-2 py-3.5 bg-slate-800 text-white font-bold rounded-2xl hover:bg-slate-900 active:scale-95 transition-all shadow-md text-sm">
              📥 Download PDF
            </a>
            ${customer.phone ? `
              <a href="https://wa.me/91${customer.phone}" target="_blank" class="flex-1 flex items-center justify-center gap-2 py-3.5 bg-white text-slate-700 border border-slate-200 font-bold rounded-2xl hover:bg-slate-50 active:scale-95 transition-all shadow-sm text-sm">
                💬 Contact Store
              </a>
            ` : ''}
          </div>

          <p class="text-center text-[10px] text-slate-400 mt-12 tracking-wide uppercase">
            Powered by Grahbook Pro — Digital Ledger System
          </p>
        </div>
      </body>
      </html>
    `;
    res.send(html);
  } catch (error) {
    console.error('Error rendering web invoice:', error);
    res.status(500).send('Server Error');
  }
});

// GET /view/customer/:id/statement — Public customer statement viewer
app.get('/view/customer/:id/statement', async (req, res) => {
  try {
    const db = await readDB();
    const customer = db.customers.find(c => c.id === req.params.id);
    if (!customer) {
      return res.status(404).send('<h1>Customer Not Found / ग्राहक नहीं मिला</h1>');
    }

    const sid = customer.store_id || 'default';
    const store = (db.stores || []).find(s => s.id === sid);
    const shop = store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          upi_id: store.upi_id || 'sharmakhata@upi',
          gstin: store.gstin || ''
        }
      : {
          name: db.shop?.name || 'General Store',
          owner: db.shop?.owner || '',
          phone: db.shop?.phone || '',
          address: db.shop?.address || '',
          upi_id: db.shop?.upi_id || 'sharmakhata@upi',
          gstin: db.shop?.gstin || ''
        };

    const customerTransactions = (db.transactions || []).filter(t => t.customer_id === customer.id);
    const customerBills = (db.bills || []).filter(b => b.customer_id === customer.id);
    const balance = getCustomerOutstanding(customer.id, db.transactions || [], db.bills || []);

    const cleanShopName = (shop.name || 'Store').replace(/[^a-zA-Z0-9 ]/g, '').trim();
    const upiUri = `upi://pay?pa=${encodeURIComponent(shop.upi_id)}&pn=${encodeURIComponent(cleanShopName)}&am=${balance.toFixed(2)}&cu=INR&tn=${encodeURIComponent('Statement Pay')}`;

    // Combine bills & transactions chronologically
    const ledgerItems = [];
    customerBills.forEach(b => {
      ledgerItems.push({
        date: new Date(b.created_at),
        type: 'bill',
        id: b.id,
        desc: `Bill #${b.id.substring(0,8).toUpperCase()}`,
        amount: b.total,
        isCredit: true,
        status: b.status
      });
    });
    customerTransactions.forEach(t => {
      ledgerItems.push({
        date: new Date(t.timestamp),
        type: t.type,
        id: t.id,
        desc: t.note || (t.type === 'payment' ? 'Payment Received' : 'Credit Entry'),
        amount: t.amount,
        isCredit: t.type === 'credit',
        status: 'completed'
      });
    });

    // Sort chronologically (oldest first or newest first, let's do newest first for statement readability)
    ledgerItems.sort((a, b) => b.date - a.date);

    const html = `
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Ledger Statement — ${escapeHtml(customer.name)}</title>
        <script src="https://cdn.tailwindcss.com"></script>
        <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
        <style>
          body { font-family: 'Outfit', sans-serif; }
          .balance-card {
            background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
          }
        </style>
      </head>
      <body class="bg-slate-50 min-h-screen text-slate-800 pb-20">
        <div class="max-w-xl mx-auto px-4 pt-6">

          <!-- Header / Status -->
          <div class="flex items-center justify-between mb-5">
            <span class="text-xs font-bold uppercase tracking-wider text-slate-400">Statement Viewer</span>
            <span class="text-xs font-semibold text-slate-500">${new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</span>
          </div>

          <!-- Shop Details Block -->
          <div class="bg-white rounded-3xl p-6 shadow-md border border-slate-100 mb-6 flex flex-col sm:flex-row justify-between items-start gap-4">
            <div>
              <h2 class="text-xl font-extrabold text-slate-800">${escapeHtml(shop.name)}</h2>
              ${shop.owner ? `<p class="text-xs text-slate-400 mt-0.5">Prop: ${escapeHtml(shop.owner)}</p>` : ''}
              ${shop.address ? `<p class="text-xs text-slate-500 mt-2">📍 ${escapeHtml(shop.address)}</p>` : ''}
              ${shop.gstin ? `<p class="text-xs text-slate-500 mt-1">🧾 GSTIN: ${escapeHtml(shop.gstin)}</p>` : ''}
            </div>
            ${shop.phone ? `
              <div class="sm:text-right border-t sm:border-t-0 border-slate-100 pt-3 sm:pt-0 w-full sm:w-auto">
                <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Support Contact</p>
                <p class="text-sm font-semibold text-indigo-600 mt-0.5">📞 ${escapeHtml(shop.phone)}</p>
              </div>
            ` : ''}
          </div>

          <!-- Customer details & Outstanding Balance -->
          <div class="balance-card rounded-3xl p-6 shadow-xl text-white mb-6">
            <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Statement For / ग्राहक</p>
            <h3 class="text-lg font-extrabold mt-1">${escapeHtml(customer.name)}</h3>
            ${customer.phone ? `<p class="text-xs text-slate-400 mt-0.5">📞 ${escapeHtml(customer.phone)}</p>` : ''}

            <div class="mt-6 pt-5 border-t border-slate-700/60 flex justify-between items-end">
              <div>
                <p class="text-xs text-slate-400 font-medium">Outstanding Balance / बाकी राशि</p>
                <p class="text-3xl font-extrabold mt-1 ${balance > 0 ? 'text-rose-400' : 'text-emerald-400'}">
                  ₹${balance.toFixed(2)}
                </p>
              </div>
              <span class="inline-flex items-center px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                balance > 0 ? 'bg-rose-500/20 text-rose-300' : 'bg-emerald-500/20 text-emerald-300'
              }">
                ${balance > 0 ? 'Due / देय' : 'No Due / चुकता'}
              </span>
            </div>
          </div>

          <!-- Interactive UPI Payment Section -->
          ${balance > 0 ? `
            <div class="bg-gradient-to-br from-amber-50 to-orange-50 rounded-3xl border border-amber-200/60 p-6 shadow-md mb-6 text-center animate-scale-in">
              <h3 class="text-lg font-bold text-amber-900 mb-1 flex items-center justify-center gap-1.5">
                ⚡ Instant Payment / तुरंत भुगतान
              </h3>
              <p class="text-xs text-amber-700/80 mb-5">Click below to pay via GPay, PhonePe, Paytm, or scan QR code</p>

              <!-- Mobile Pay Now Button -->
              <a href="${upiUri}" class="w-full flex items-center justify-center gap-2 py-4 bg-emerald-600 text-white font-extrabold rounded-2xl shadow-lg hover:bg-emerald-700 hover:shadow-emerald-200 active:scale-95 transition-all text-base mb-6">
                <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
                PAY OUTSTANDING (₹${balance.toFixed(2)})
              </a>

              <!-- Desktop QR code -->
              <div class="hidden sm:block border-t border-amber-200/60 pt-5">
                <div class="bg-white p-4 inline-block rounded-2xl shadow-inner border border-amber-100 mb-2">
                  <img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(upiUri)}" alt="UPI QR Code" class="h-36 w-36">
                </div>
                <p class="text-[10px] text-amber-600 font-bold tracking-wide uppercase">Scan with GPay/PhonePe/Paytm</p>
              </div>
            </div>
          ` : ''}

          <!-- Ledger List -->
          <div class="bg-white rounded-3xl p-6 shadow-md border border-slate-100 mb-6">
            <h4 class="text-base font-extrabold text-slate-800 mb-4">Transaction History / हिसाब किताब</h4>

            <div class="space-y-3.5">
              ${ledgerItems.length === 0 ? `
                <p class="text-center text-sm text-slate-400 py-6">No transactions found / कोई लेन-देन नहीं है</p>
              ` : ledgerItems.map(item => {
                const isDebit = !item.isCredit;
                const statusColor = item.status === 'unpaid' ? 'text-rose-500' : 'text-slate-400';
                return `
                  <div class="flex justify-between items-center border-b border-slate-100/80 pb-3 last:border-b-0 last:pb-0">
                    <div>
                      <span class="text-xs font-semibold text-slate-400">
                        ${new Date(item.date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                      </span>
                      <p class="text-sm font-bold text-slate-800 mt-0.5">${escapeHtml(item.desc)}</p>
                      ${item.type === 'bill' ? `
                        <span class="text-[10px] uppercase font-bold tracking-wider ${statusColor}">
                          ${item.status === 'paid' ? 'Paid' : 'Unpaid'}
                        </span>
                      ` : ''}
                    </div>
                    <div class="text-right">
                      <span class="text-sm font-extrabold ${isDebit ? 'text-emerald-600' : 'text-rose-500'}">
                        ${isDebit ? '-' : '+'}${item.amount.toFixed(2)}
                      </span>
                      <p class="text-[10px] text-slate-400 font-medium mt-0.5">${isDebit ? 'Payment' : 'Credit Given'}</p>
                    </div>
                  </div>
                `;
              }).join('')}
            </div>
          </div>

          <!-- PDF & Action Footer -->
          <div class="flex gap-3">
            <a href="/api/customer/${encodeURIComponent(customer.id)}/statement/pdf" class="flex-1 flex items-center justify-center gap-2 py-3.5 bg-slate-800 text-white font-bold rounded-2xl hover:bg-slate-900 active:scale-95 transition-all shadow-md text-sm">
              📥 Download PDF
            </a>
            ${customer.phone ? `
              <a href="https://wa.me/91${customer.phone}" target="_blank" class="flex-1 flex items-center justify-center gap-2 py-3.5 bg-white text-slate-700 border border-slate-200 font-bold rounded-2xl hover:bg-slate-50 active:scale-95 transition-all shadow-sm text-sm">
                💬 Contact Store
              </a>
            ` : ''}
          </div>

          <p class="text-center text-[10px] text-slate-400 mt-12 tracking-wide uppercase">
            Powered by Grahbook Pro — Digital Ledger System
          </p>
        </div>
      </body>
      </html>
    `;
    res.send(html);
  } catch (error) {
    console.error('Error rendering web statement:', error);
    res.status(500).send('Server Error');
  }
});

// GET /api/report/:date/pdf — Generate daily report PDF
app.get('/api/report/:date/pdf', pdfAuthMiddleware, async (req, res) => {
  try {
    const targetDate = req.params.date;
    const storeId = req.query.storeId || 'default';
    const db = await readDB();

    const store = (db.stores || []).find(s => s.id === storeId);
    const shop = store
      ? {
          name: store.store_name,
          owner: store.owner_name,
          phone: store.phone,
          address: store.address,
          store_id: storeId,
        }
      : (storeId === 'default' && db.shop ? db.shop : {});

    const storeBills = (db.bills || []).filter(b => (b.store_id || 'default') === storeId);
    const storeTransactions = (db.transactions || []).filter(t => (t.store_id || 'default') === storeId);
    const storeCustomers = (db.customers || []).filter(c => (c.store_id || 'default') === storeId);

    const billsToday = storeBills.filter(b => b.created_at && b.created_at.startsWith(targetDate));
    const billsTotal = billsToday.reduce((sum, b) => sum + (b.total || 0), 0);
    const collectionsToday = storeTransactions.filter(t => t.type === 'payment' && t.timestamp && t.timestamp.startsWith(targetDate));
    const paymentTotal = collectionsToday.reduce((sum, t) => sum + (t.amount || 0), 0);
    const creditsToday = storeTransactions.filter(t => t.type === 'credit' && t.timestamp && t.timestamp.startsWith(targetDate));
    const creditTotal = creditsToday.reduce((sum, t) => sum + (t.amount || 0), 0);
    const paidBills = billsToday.filter(b => b.status === 'paid').length;
    const unpaidBills = billsToday.length - paidBills;
    const netCollection = paymentTotal - creditTotal;

    // Overall outstanding for this store
    const totalOutstanding = storeCustomers.reduce((sum, c) => {
      const bal = getCustomerOutstanding(c.id, storeTransactions, storeBills);
      return sum + (bal > 0 ? bal : 0);
    }, 0);

    // Colors
    const INDIGO = '#4F46E5';
    const INDIGO_LIGHT = '#EEF2FF';
    const EMERALD = '#059669';
    const AMBER = '#D97706';
    const RED = '#DC2626';
    const SLATE_900 = '#0F172A';
    const SLATE_600 = '#475569';
    const SLATE_400 = '#94A3B8';
    const WHITE = '#FFFFFF';
    const ROW_ALT = '#F8FAFC';
    const PAGE_W = 595.28;
    const PAGE_H = 841.89;

    // Create PDF — margin 0 for full-width banner
    const doc = new PDFDocument({ size: 'A4', margin: 0 });

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=daily-report-${targetDate}.pdf`);
    await new Promise((resolve, reject) => {
      doc.on('finish', resolve);
      doc.on('error', reject);
      doc.pipe(res);

    // ── Indigo header banner ────────────────────────────────────────────────────
    doc.save();
    doc.rect(0, 0, PAGE_W, 100).fill(INDIGO);
    doc.fontSize(24).font('Helvetica-Bold').fillColor(WHITE)
       .text(shop.name || 'GENERAL STORE', 40, 28, { width: PAGE_W - 80, align: 'center' });
    doc.fontSize(10).font('Helvetica').fillColor('#C7D2FE')
       .text((shop.address || '') + (shop.phone ? `  •  +91 ${shop.phone}` : ''), 40, 58, { width: PAGE_W - 80, align: 'center' });
    doc.restore();

    // ── Report title ────────────────────────────────────────────────────────────
    let y = 125;
    doc.fontSize(18).font('Helvetica-Bold').fillColor(SLATE_900)
       .text('DAILY SALES REPORT', 40, y, { width: PAGE_W - 80, align: 'center' });
    y += 28;
    doc.fontSize(11).font('Helvetica').fillColor(SLATE_600)
       .text(`Report Date: ${fmtDate(targetDate)}`, 40, y, { width: PAGE_W - 80, align: 'center' });
    y += 35;

    // ── 4-column summary cards ──────────────────────────────────────────────────
    const cardW = (PAGE_W - 80 - 18) / 4;  // 4 cards with 6px gaps
    const cardH = 62;
    const cards = [
      { label: 'Total Sales',     value: fmtRs(billsTotal),    color: INDIGO },
      { label: 'Collections',     value: fmtRs(paymentTotal),  color: EMERALD },
      { label: 'Credits Given',   value: fmtRs(creditTotal),   color: AMBER },
      { label: 'Net Collection',  value: fmtRs(netCollection), color: netCollection >= 0 ? EMERALD : RED },
    ];

    cards.forEach((card, i) => {
      const cx = 40 + i * (cardW + 6);
      // Card background
      doc.save();
      doc.roundedRect(cx, y, cardW, cardH, 8).fill(WHITE);
      doc.roundedRect(cx, y, cardW, cardH, 8).strokeColor('#E2E8F0').lineWidth(1).stroke();
      doc.restore();
      // Color accent bar
      doc.save();
      doc.rect(cx, y, cardW, 4).fill(card.color);
      doc.restore();
      // Label
      doc.fontSize(8).font('Helvetica').fillColor(SLATE_400)
         .text(card.label.toUpperCase(), cx + 8, y + 12, { width: cardW - 16 });
      // Value
      doc.fontSize(14).font('Helvetica-Bold').fillColor(card.color)
         .text(card.value, cx + 8, y + 28, { width: cardW - 16 });
    });
    y += cardH + 30;

    // ── Bills summary section ───────────────────────────────────────────────────
    doc.save();
    doc.roundedRect(40, y, PAGE_W - 80, 24, 6).fill(INDIGO);
    doc.fontSize(11).font('Helvetica-Bold').fillColor(WHITE)
       .text('BILLS SUMMARY', 55, y + 6, { width: PAGE_W - 110 });
    doc.restore();
    y += 30;

    // Table header
    const colX = [50, 300, 500];
    doc.save();
    doc.rect(40, y, PAGE_W - 80, 22).fill(INDIGO_LIGHT);
    doc.fontSize(9).font('Helvetica-Bold').fillColor(SLATE_900);
    doc.text('METRIC', colX[0], y + 6);
    doc.text('VALUE', colX[2], y + 6, { width: PAGE_W - 80 - (colX[2] - 40), align: 'right' });
    doc.restore();
    y += 22;

    const billRows = [
      ['Total Bills Created', billsToday.length.toString()],
      ['Bills Paid',          paidBills.toString()],
      ['Bills Unpaid',        unpaidBills.toString()],
      ['Total Bill Amount',   fmtRs(billsTotal)],
    ];

    billRows.forEach((row, i) => {
      const bg = i % 2 === 0 ? WHITE : ROW_ALT;
      doc.save();
      doc.rect(40, y, PAGE_W - 80, 22).fill(bg);
      doc.fontSize(10).font('Helvetica').fillColor(SLATE_600)
         .text(row[0], colX[0], y + 5);
      doc.font('Helvetica-Bold').fillColor(SLATE_900)
         .text(row[1], colX[2], y + 5, { width: PAGE_W - 80 - (colX[2] - 40), align: 'right' });
      doc.restore();
      y += 22;
    });
    y += 25;

    // ── Financial summary section ───────────────────────────────────────────────
    doc.save();
    doc.roundedRect(40, y, PAGE_W - 80, 24, 6).fill(INDIGO);
    doc.fontSize(11).font('Helvetica-Bold').fillColor(WHITE)
       .text('FINANCIAL SUMMARY', 55, y + 6, { width: PAGE_W - 110 });
    doc.restore();
    y += 30;

    // Table header
    doc.save();
    doc.rect(40, y, PAGE_W - 80, 22).fill(INDIGO_LIGHT);
    doc.fontSize(9).font('Helvetica-Bold').fillColor(SLATE_900);
    doc.text('METRIC', colX[0], y + 6);
    doc.text('AMOUNT', colX[2], y + 6, { width: PAGE_W - 80 - (colX[2] - 40), align: 'right' });
    doc.restore();
    y += 22;

    const finRows = [
      ['Total Sales',           fmtRs(billsTotal),     INDIGO],
      ['Total Collections',     fmtRs(paymentTotal),   EMERALD],
      ['Total Credits Given',   fmtRs(creditTotal),    AMBER],
      ['Net Collection',        fmtRs(netCollection),  netCollection >= 0 ? EMERALD : RED],
    ];

    finRows.forEach((row, i) => {
      const bg = i % 2 === 0 ? WHITE : ROW_ALT;
      doc.save();
      doc.rect(40, y, PAGE_W - 80, 22).fill(bg);
      doc.fontSize(10).font('Helvetica').fillColor(SLATE_600)
         .text(row[0], colX[0], y + 5);
      doc.font('Helvetica-Bold').fillColor(row[2])
         .text(row[1], colX[2], y + 5, { width: PAGE_W - 80 - (colX[2] - 40), align: 'right' });
      doc.restore();
      y += 22;
    });
    y += 25;

    // ── Overall outstanding card ────────────────────────────────────────────────
    doc.save();
    doc.roundedRect(40, y, PAGE_W - 80, 60, 8).fill(INDIGO_LIGHT);
    doc.roundedRect(40, y, PAGE_W - 80, 60, 8).strokeColor(INDIGO).lineWidth(1.5).stroke();
    doc.restore();
    doc.fontSize(9).font('Helvetica-Bold').fillColor(INDIGO)
       .text('OVERALL OUTSTANDING', 55, y + 12, { width: PAGE_W - 110 });
    doc.fontSize(22).font('Helvetica-Bold').fillColor(INDIGO)
       .text(fmtRs(totalOutstanding), 55, y + 30, { width: PAGE_W - 110 });
    doc.fontSize(9).font('Helvetica').fillColor(SLATE_600)
       .text(`Across ${db.customers.length} customers`, 350, y + 36, { width: PAGE_W - 80 - 310, align: 'right' });
    y += 80;

    // ── Today's bills detail table ──────────────────────────────────────────────
    if (billsToday.length > 0) {
      doc.save();
      doc.roundedRect(40, y, PAGE_W - 80, 24, 6).fill(INDIGO);
      doc.fontSize(11).font('Helvetica-Bold').fillColor(WHITE)
         .text("TODAY'S BILLS", 55, y + 6, { width: PAGE_W - 110 });
      doc.restore();
      y += 30;

      // Table header
      const detailCols = [50, 80, 260, 370, 470];
      doc.save();
      doc.rect(40, y, PAGE_W - 80, 22).fill(INDIGO_LIGHT);
      doc.fontSize(8).font('Helvetica-Bold').fillColor(SLATE_900);
      doc.text('#',       detailCols[0], y + 6);
      doc.text('ID',      detailCols[1], y + 6);
      doc.text('CUSTOMER', detailCols[2], y + 6);
      doc.text('AMOUNT',  detailCols[3], y + 6, { width: 90, align: 'right' });
      doc.text('STATUS',  detailCols[4], y + 6, { width: PAGE_W - 80 - (detailCols[4] - 40), align: 'right' });
      doc.restore();
      y += 22;

      billsToday.slice(0, 20).forEach((bill, i) => {
        if (y > PAGE_H - 80) return; // avoid overflow
        const bg = i % 2 === 0 ? WHITE : ROW_ALT;
        doc.save();
        doc.rect(40, y, PAGE_W - 80, 22).fill(bg);
        const cust = db.customers.find(c => c.id === bill.customer_id);
        const isPaid = bill.status === 'paid';
        doc.fontSize(9).font('Helvetica').fillColor(SLATE_400)
           .text((i + 1).toString(), detailCols[0], y + 5);
        doc.font('Helvetica').fillColor(SLATE_600)
           .text(bill.id ? bill.id.toString().slice(0, 8) : '-', detailCols[1], y + 5);
        doc.font('Helvetica').fillColor(SLATE_900)
           .text(cust ? cust.name : 'Unknown', detailCols[2], y + 5, { width: 100 });
        doc.font('Helvetica-Bold').fillColor(SLATE_900)
           .text(fmtRs(bill.total), detailCols[3], y + 5, { width: 90, align: 'right' });
        doc.font('Helvetica-Bold').fillColor(isPaid ? EMERALD : RED)
           .text(isPaid ? 'PAID' : 'UNPAID', detailCols[4], y + 5, { width: PAGE_W - 80 - (detailCols[4] - 40), align: 'right' });
        doc.restore();
        y += 22;
      });
      y += 10;
    }

    // ── Footer ──────────────────────────────────────────────────────────────────
    doc.save();
    doc.rect(0, 760, PAGE_W, 82).fill('#F1F5F9');
    doc.fontSize(9).font('Helvetica-Bold').fillColor(INDIGO)
       .text(shop.name || 'GENERAL STORE', 40, 775, { width: PAGE_W - 80, align: 'center' });
    doc.fontSize(8).font('Helvetica').fillColor(SLATE_400)
       .text('This is a computer-generated report  •  Generated on ' + new Date().toLocaleString('en-IN'), 40, 790, { width: PAGE_W - 80, align: 'center' });
    doc.restore();

    // Finalize PDF
    doc.end();
    });

  } catch (error) {
    console.error('Error generating report PDF:', error);
    doc.destroy();
    if (!res.headersSent) {
      res.removeHeader('Content-Disposition');
      res.removeHeader('Content-Type');
      res.status(500).json({ success: false, message: 'Failed to generate report PDF' });
    }
  }
});

// ─── START SERVER ──────────────────────────────────────────────────────────────

// Serve the landing page
app.get('/', (req, res) => {
  res.sendFile(__dirname + '/index.html');
});

// Serve the dashboard HTML file
app.get('/dashboard.html', (req, res) => {
  res.sendFile(__dirname + '/dashboard.html');
});

app.get('/customers.html', (req, res) => {
  res.sendFile(__dirname + '/customers.html');
});

app.get('/create-invoice.html', (req, res) => {
  res.sendFile(__dirname + '/create-invoice.html');
});

app.get('/billing.html', (req, res) => {
  res.sendFile(__dirname + '/billing.html');
});

// Serve PWA files
app.get('/manifest.json', (req, res) => {
  res.sendFile(__dirname + '/manifest.json');
});

app.get('/sw.js', (req, res) => {
  res.sendFile(__dirname + '/sw.js');
});

// ─── ONE-TIME MIGRATION: Normalize all existing phone numbers ────────────────
async function migratePhoneNumbers() {
  try {
    const db = await readDB();
    let changed = false;

    if (db.customers) {
      for (const c of db.customers) {
        const normalized = normalizePhone(c.phone);
        if (c.phone !== normalized) {
          c.phone = normalized;
          changed = true;
        }
      }
    }
    if (db.staff) {
      for (const s of db.staff) {
        const normalized = normalizePhone(s.phone);
        if (s.phone !== normalized) {
          s.phone = normalized;
          changed = true;
        }
      }
    }
    if (db.stores) {
      for (const s of db.stores) {
        const normalized = normalizePhone(s.phone);
        if (s.phone !== normalized) {
          s.phone = normalized;
          changed = true;
        }
      }
    }

    if (changed) {
      await writeDB(db);
      console.log('✅ Phone number migration completed — all phones normalized');
    } else {
      console.log('✓ Phone numbers already normalized');
    }
  } catch (e) {
    console.error('Phone migration error (non-fatal):', e.message);
  }
}

// ─── GLOBAL ERROR MIDDLEWARE ──────────────────────────────────────────────────
// Catches all unhandled errors and prevents hung connections
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err);
  if (res.headersSent) return next(err);
  res.status(500).json({ success: false, message: 'Internal server error' });
});

// ─── UNHANDLED PROMISE REJECTIONS ────────────────────────────────────────────
process.on('unhandledRejection', (reason, promise) => {
  console.error('Unhandled Rejection:', reason);
});

process.on('uncaughtException', (err) => {
  console.error('Uncaught Exception:', err);
  // Don't crash in production — log and continue
});

app.listen(PORT, async () => {
  console.log(`🚀 Store Bot running on port ${PORT}`);
  console.log(`📱 Webhook: POST /webhook`);
  console.log(`🌐 Dashboard: http://localhost:${PORT}`);
  console.log(`🌐 Dashboard API: GET /api/db`);
  await migratePhoneNumbers();
  // Start daily 9 AM report scheduler
  scheduleDaily(9, 0, sendDailyReport);
});
