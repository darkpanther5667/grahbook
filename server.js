require('dotenv').config();
const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const OpenAI = require('openai');
const bcrypt = require('bcryptjs');
const PDFDocument = require('pdfkit');

// Initialize OpenRouter API
const openrouterApiKey = process.env.OPENROUTER_API_KEY;
let openai = null;
if (openrouterApiKey && openrouterApiKey !== 'YOUR_OPENROUTER_API_KEY') {
  openai = new OpenAI({
    apiKey: openrouterApiKey,
    baseURL: 'https://openrouter.ai/api/v1'
  });
}

const app = express();
app.use(express.json());
app.set('trust proxy', 1);

// Allow the local dashboard (file:// origin) to call the API
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'db.json');

// ─── MOBILE API KEY AUTH ──────────────────────────────────────────────────────

function requiresMobileApiKey(req) {
  if (!req.path.startsWith('/api/')) return false;

  // Allow PDF endpoints to be fetched by WhatsApp/clients without an app key.
  if (req.method === 'GET') {
    if (req.path === '/api/test-db') return false;
    if (req.path === '/api/test-wa') return false;
    if (/^\/api\/bill\/[^/]+\/pdf$/.test(req.path)) return false;
    if (/^\/api\/customer\/[^/]+\/statement\/pdf$/.test(req.path)) return false;
    if (/^\/api\/report\/[^/]+\/pdf$/.test(req.path)) return false;
  }

  // WhatsApp webhook is not under /api.
  return true;
}

function mobileApiKeyMiddleware(req, res, next) {
  if (!requiresMobileApiKey(req)) return next();

  const expectedKey = process.env.MOBILE_API_KEY;
  if (!expectedKey) {
    return res.status(500).json({ success: false, message: 'Server misconfigured: MOBILE_API_KEY missing' });
  }

  const providedKey = req.get('X-API-KEY');
  if (!providedKey || providedKey !== expectedKey) {
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
  if (req.path.startsWith('/api/store/')) return false;
  if (req.path === '/api/auth/request-code') return false;
  if (req.path === '/api/auth/verify-code') return false;
  if (req.path === '/api/auth/login') return false;

  // PDF endpoints are fetched by WhatsApp directly.
  if (req.method === 'GET') {
    if (req.path === '/api/test-db') return false;
    if (req.path === '/api/test-wa') return false;
    if (/^\/api\/bill\/[^/]+\/pdf$/.test(req.path)) return false;
    if (/^\/api\/customer\/[^/]+\/statement\/pdf$/.test(req.path)) return false;
    if (/^\/api\/report\/[^/]+\/pdf$/.test(req.path)) return false;
  }

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
        }
      : (sid === 'default' && db.shop ? db.shop : {});

  return {
    shop,
    customers: (db.customers || []).filter(c => (c.store_id || 'default') === sid),
    transactions: (db.transactions || []).filter(t => (t.store_id || 'default') === sid),
    bills: (db.bills || []).filter(b => (b.store_id || 'default') === sid),
    staff: (db.staff || []).filter(s => (s.store_id || 'default') === sid),
    stores: db.stores || [],
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
    /\(?\s*ID:\s*([c_][a-z0-9]+)\s*\)?/i,
    /customer\s+ID:\s*([c_][a-z0-9]+)/i,
    /customer\s+([c_][a-z0-9]+)/i,
    /\b([c_][a-z0-9]{8,})\b/
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
async function writeDB(data) {
  if (dbWriteLock) {
    // Wait 50ms and retry
    await new Promise(r => setTimeout(r, 50));
    return await writeDB(data);
  }
  dbWriteLock = true;
  try {
    const database = await connectDB();
    if (!database) {
      fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf8');
      cachedDB = data;
      return;
    }

    // Overwrite collections (mimics the db.json full overwrite behavior for backwards compatibility)
    for (const col of ['shop', 'customers', 'transactions', 'bills', 'staff', 'stores']) {
      if (data[col]) {
        await database.collection(col).deleteMany({});
        let docs = Array.isArray(data[col]) ? data[col] : [data[col]];
        if (docs.length > 0) await database.collection(col).insertMany(docs);
      }
    }
    cachedDB = data;
  } catch (error) {
    console.error('Error writing to MongoDB:', error);
  } finally {
    dbWriteLock = false;
  }
}

// ─── UTILITY HELPERS ───────────────────────────────────────────────────────────

// Format date as DD/MM/YYYY (Indian standard)
function fmtDate(isoStr) {
  return new Date(isoStr).toLocaleDateString('en-GB');
}

// Format rupee amount
function fmtRs(amount) {
  return `₹${Number(amount).toLocaleString('en-IN')}`;
}

// Generate a short random ID
function genId(prefix) {
  return prefix + '_' + Math.random().toString(36).substring(2, 9);
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

async function recordPaymentTool(customerId, amount, note, staffPhone, storeId) {
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
  currentBill.items.push({ name: itemName, qty: quantity, price: Number(price) });
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
    items: [{ name: 'General Grocery Item', qty: 1, price: Number(amount) }],
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
    // If no unpaid bill, create a simple bill for demo purposes
    const newBillId = genId('b');
    const timestampIso = new Date().toISOString();
    const newBill = {
      id: newBillId,
      customer_id: customerId,
      items: [{ name: 'General Grocery Item', qty: 1, price: 100 }],
      total: 100,
      status: 'unpaid',
      store_id: storeId || 'default',
      created_at: timestampIso,
      paid_at: null
    };
    db.bills.push(newBill);
    await writeDB(db);
    return {
      success: true,
      billId: newBillId,
      pdfUrl: `/api/bill/${newBillId}/pdf`,
      message: 'PDF generated for newly created bill'
    };
  }

  return {
    success: true,
    billId: unpaidBill.id,
    pdfUrl: `/api/bill/${unpaidBill.id}/pdf`,
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
  const explicitIdMatch = text.match(/\(?\s*ID:\s*([c_][a-z0-9]+)\s*\)?/i);
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

async function askOpenRouterWithTools(messageText, staffPhone, storeId) {
  if (!openai) {
    const currentApiKey = process.env.OPENROUTER_API_KEY;
    if (currentApiKey && currentApiKey !== 'YOUR_OPENROUTER_API_KEY') {
      openai = new OpenAI({
        apiKey: currentApiKey,
        baseURL: 'https://openrouter.ai/api/v1'
      });
    }
  }

  if (!openai) {
    console.warn('⚠️ OpenRouter AI is not configured. OPENROUTER_API_KEY is missing.');
    return '⚠️ OpenRouter API key missing. Please configure it in your .env file.';
  }

  const modelName = process.env.OPENROUTER_MODEL || 'deepseek/deepseek-r1:free';
  const conversationContext = getConversationContext(staffPhone);

  try {
    const db = await readDB();
    const storeDb = await readStoreDB(storeId);
    const shopInfo = storeDb.shop || {};

    const customerListString = (storeDb.customers || []).length > 0
      ? (storeDb.customers || []).map(c => `  ID: ${c.id} — ${c.name} (${c.phone})`).join('\n')
      : '  (No customers registered yet.)';

    const todayBills = (storeDb.bills || []).filter(b => b.created_at?.startsWith(new Date().toISOString().substring(0, 10)));
    const todaySales = todayBills.reduce((s, b) => s + (b.total || 0), 0);
    const todayCollections = (storeDb.transactions || [])
      .filter(t => t.type === 'payment' && t.timestamp?.startsWith(new Date().toISOString().substring(0, 10)))
      .reduce((s, t) => s + (t.amount || 0), 0);

    const systemInstruction = `Tu ${shopInfo.name || 'store'} ka AI assistant hai. Shop owner: ${shopInfo.owner || 'unknown'}, Address: ${shopInfo.address || 'unknown'}.

Store ka aaj ka data:
📊 Aaj ki sales: ₹${todaySales}
💰 Aaj ka collection: ₹${todayCollections}
👥 Total customers: ${(storeDb.customers || []).length}

TERE PAAS YEH CUSTOMERS HAIN (INHI IDs use karna, kabhi bina ID mat banao):
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

BASIC RULES:
1. Jo language mein user baat kare, usi mein jawab de (hinglish/hindi/english)
2. Customer ID kabhi bina database ke mat banao — upar di hui list mein se lo ya addCustomerTool se milne ke baad
3. Jo customer list mein nahi hai, pehle use add karo via addCustomerTool
4. Ek turn mein ek se zyada tool call kar sakta hai (e.g. pehle customer add, phir bill)
5. Paise hamesha number mein bhejo (e.g. 500, "five hundred" nahi)
6. Jawab mein emoji aur Indian currency (₹1,000) use karo
7. Agar tool error de, to wahi error user ko batao — success mat banao
8. Users ka context yaad rakho — jo customer ID previous turn mein mili, wapas mat poocho`;

    // Build conversation context string
    let contextString = '';
    let lastCustomerId = null;

    if (conversationContext.length > 0) {
      contextString = '\n\nPichli baatein (yaad rakho):\n';
      conversationContext.forEach((ctx, idx) => {
        contextString += `${idx + 1}. User: "${ctx.message}"\n   Assistant: "${ctx.response.substring(0, 150)}..."\n`;
        const extractedId = extractCustomerId(ctx.message);
        if (extractedId) { lastCustomerId = extractedId; }
      });
      if (lastCustomerId) {
        contextString += `\n⚠️ User ne pehle customer ID batayi thi: ${lastCustomerId}. Use karo agar wahi customer ho.`;
      }
    }

    const messages = [
      { role: 'system', content: `${systemInstruction}\n${contextString}` },
      { role: 'user', content: messageText }
    ];

    // DeepSeek-R1 ko tool definitions bhejni zaroori hai function calling ke liye
    const tools = [
      { type: 'function', function: { name: 'addCustomerTool', description: 'Naya customer add karna (name, phone)', parameters: { type: 'object', properties: { name: { type: 'string' }, phone: { type: 'string' } }, required: ['name', 'phone'] } } },
      { type: 'function', function: { name: 'recordPaymentTool', description: 'Payment record karna (customerId, amount, note)', parameters: { type: 'object', properties: { customerId: { type: 'string' }, amount: { type: 'number' }, note: { type: 'string' } }, required: ['customerId', 'amount'] } } },
      { type: 'function', function: { name: 'addCreditTool', description: 'Udhaar/credit add karna (customerId, amount, note)', parameters: { type: 'object', properties: { customerId: { type: 'string' }, amount: { type: 'number' }, note: { type: 'string' } }, required: ['customerId', 'amount'] } } },
      { type: 'function', function: { name: 'addItemToUnpaidBillTool', description: 'Bill mein item add karna (customerId, itemName, price, qty)', parameters: { type: 'object', properties: { customerId: { type: 'string' }, itemName: { type: 'string' }, price: { type: 'number' }, qty: { type: 'number' } }, required: ['customerId', 'itemName', 'price'] } } },
      { type: 'function', function: { name: 'generateBillTool', description: 'Fixed amount ka bill banana (customerId, amount)', parameters: { type: 'object', properties: { customerId: { type: 'string' }, amount: { type: 'number' } }, required: ['customerId', 'amount'] } } },
      { type: 'function', function: { name: 'markBillAsPaidTool', description: 'Bill paid mark karna (customerId)', parameters: { type: 'object', properties: { customerId: { type: 'string' } }, required: ['customerId'] } } },
      { type: 'function', function: { name: 'getCustomerBalancesTool', description: 'Sab customers ke outstanding balances dikhana', parameters: { type: 'object', properties: {} } } },
      { type: 'function', function: { name: 'getTodaySalesReportTool', description: 'Aaj ki sales report dikhana', parameters: { type: 'object', properties: {} } } },
      { type: 'function', function: { name: 'getShopDetailsTool', description: 'Shop ka naam, owner, address dikhana', parameters: { type: 'object', properties: {} } } },
      { type: 'function', function: { name: 'getCustomersListTool', description: 'Saare customers ki list dikhana', parameters: { type: 'object', properties: {} } } },
      { type: 'function', function: { name: 'getBillPdfTool', description: 'Bill ka PDF generate karna (customerId)', parameters: { type: 'object', properties: { customerId: { type: 'string' } }, required: ['customerId'] } } },
      { type: 'function', function: { name: 'getCustomerStatementPdfTool', description: 'Customer statement ka PDF (customerId)', parameters: { type: 'object', properties: { customerId: { type: 'string' } }, required: ['customerId'] } } },
      { type: 'function', function: { name: 'getDailyReportPdfTool', description: 'Daily report PDF (date YYYY-MM-DD optional)', parameters: { type: 'object', properties: { date: { type: 'string' } } } } }
    ];

    let response = await openai.chat.completions.create({
      model: modelName,
      messages: messages,
      tools: tools,
      tool_choice: 'auto'
    });

    // Loop for handling tool calls
    let loopCount = 0;
    while (loopCount < 5) {
      const toolCalls = response.choices[0].message.tool_calls;
      if (!toolCalls || toolCalls.length === 0) break;

      loopCount++;
      
      // Add assistant's tool call message
      messages.push(response.choices[0].message);
      
      // Execute tools and get results
      for (const toolCall of toolCalls) {
        const toolResult = await executeTool(toolCall.function.name, JSON.parse(toolCall.function.arguments), staffPhone, storeId);
        console.log(`   ↳ ${toolCall.function.name} result:`, toolResult);
        
        messages.push({
          role: 'tool',
          tool_call_id: toolCall.id,
          content: JSON.stringify(toolResult)
        });
      }

      // Get next response from OpenRouter
      response = await openai.chat.completions.create({
        model: modelName,
        messages: messages,
        tools: tools,
        tool_choice: 'auto'
      });
    }

    const finalResponse = response.choices[0].message.content.trim();
    
    // Add to conversation history
    addToHistory(staffPhone, messageText, finalResponse);
    
    return finalResponse;
  } catch (error) {
    console.error('❌ OpenRouter API error:', error.message || error);
        const errorMsg = `❌ कुछ तकनीकल समस्या हुई। कृपया दोबारा कोशिश करें या एडमिन को बताएं। 🙏`;

    if (error.status === 429) {
      return `⏳ बॉट अभी व्यस्त है। एक मिनट बाद दोबारा कोशिश करें। 🙏`;
    }
    if (error.status === 503 || error.message?.includes("network")) {
      return `📶 नेटवर्क एरर। कृपया बाद में कोशिश करें।`;
    }
    return errorMsg;
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
  function tick() {
    fn();
    setTimeout(tick, msUntilNext());
  }
  setTimeout(tick, msUntilNext());
  console.log(`📅 Daily report scheduled at ${hour}:${String(minuteIST).padStart(2,'0')} IST`);
}

async function sendDailyReport() {
  const db = await readDB();
  const todayString = new Date().toISOString().substring(0, 10);

  const billsToday = db.bills.filter(b => b.created_at.startsWith(todayString));
  const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
  const paidToday = billsToday.filter(b => b.status === 'paid').reduce((sum, b) => sum + b.total, 0);

  const collectionsToday = db.transactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(todayString));
  const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);

  const totalOutstanding = db.customers.reduce((sum, c) => {
    const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
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
    `👥 Total customers: ${db.customers.length}\n` +
    `━━━━━━━━━━━━━━━━━━━━\n` +
    `_${shop.name || 'General Store'} Bot 🤖_`;

  for (const staff of db.staff) {
    await sendWhatsAppMessage(staff.phone, report);
    console.log(`📨 Daily report sent to ${staff.name} (${staff.phone})`);
  }
}

// ─── WEBHOOK ROUTES ────────────────────────────────────────────────────────────

// GET /webhook — Meta verification
app.get('/webhook', (req, res) => {
  const verifyToken = process.env.VERIFY_TOKEN || 'sharma_store_token';
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
app.post('/webhook', async (req, res) => {
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
      return res.status(200).json({ status: 'success', action: 'unauthorized' });
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
      const pdfUrl = `http://localhost:${PORT}/api/report/${todayString}/pdf`;
      
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
      const shop = db.shop;
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
      currentBill.items.push({ name: itemName, qty: 1, price });
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
        const pdfUrl = `http://localhost:${PORT}/api/bill/${unpaidBill.id}/pdf`;
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
      const pdfUrl = `http://localhost:${PORT}/api/customer/${customerId}/statement/pdf`;
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
    // ── UNKNOWN → AI AGENT WITH TOOL USE ─────────────────────────────────────
    } else {
      console.log(`🤖 Regex did not match. Forwarding to OpenRouter AI Agent: "${bodyText}"`);
      replyText = await askOpenRouterWithTools(bodyText, staffPhone, storeId);
      console.log(`🤖 AI Agent reply: "${replyText}"`);
    }

    await sendWhatsAppMessage(staffPhone, replyText);
    return res.status(200).json({ status: 'success', action: action.type, aiUsed: action.type === 'unknown' });
  }

  return res.sendStatus(200);
});

// GET /api/test-wa — Diagnostic route to test WhatsApp API
app.get('/api/test-wa', async (req, res) => {
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
    return res.json({ status: 'Connection failed', error: error.message, stack: error.stack });
  }
});

// ─── MOBILE APP API ENDPOINTS ─────────────────────────────────────────────────────

// POST /api/auth/request-code — Request WhatsApp OTP code for login
app.post('/api/auth/request-code', async (req, res) => {
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

    const code = String(Math.floor(100000 + Math.random() * 900000));
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
    return res.status(500).json({ success: false, message: 'Failed to request code', error: error.message });
  }
});

// POST /api/auth/verify-code — Verify OTP and create a session
app.post('/api/auth/verify-code', async (req, res) => {
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
      await database.collection('login_codes').updateOne({ _id: row._id }, { $inc: { attempts: 1 } });
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
app.post('/api/auth/login', async (req, res) => {
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

// POST /api/customer/add - Add a new customer
app.post('/api/customer/add', async (req, res) => {
  try {
    const { name, phone } = req.body;
    if (!name || !phone) {
      return res.status(400).json({ success: false, message: 'Name and phone are required' });
    }
    const np = normalizePhone(phone);

    const db = await readStoreDB(req.storeId);

    // Check if customer already exists
    const existingCustomer = db.customers.find(c => c.name.toLowerCase() === name.toLowerCase() || normalizePhone(c.phone) === np);
    if (existingCustomer) {
      return res.status(400).json({ success: false, message: 'Customer already exists' });
    }

    const newCustomer = {
      id: genId('c'),
      name: name.replace(/\b\w/g, c => c.toUpperCase()),
      phone: np,
      created_at: new Date().toISOString().substring(0, 10),
      store_id: req.storeId || 'default',
    };

    db.customers.push(newCustomer);
    await writeDB(db);
    // Invalidate cache to ensure AI gets fresh data
    cachedDB = null;
    dbCacheTimestamp = 0;

    res.json({ success: true, customer: newCustomer });
  } catch (error) {
    console.error('Error adding customer:', error);
    res.status(500).json({ success: false, message: 'Failed to add customer' });
  }
});

// POST /api/payment/add - Record a payment
app.post('/api/payment/add', async (req, res) => {
  try {
    const { customerId, amount, note } = req.body;
    if (!customerId || !amount) {
      return res.status(400).json({ success: false, message: 'Customer ID and amount are required' });
    }

    const db = await readStoreDB(req.storeId);
    const customer = db.customers.find(c => c.id === customerId);
    if (!customer) {
      return res.status(404).json({ success: false, message: 'Customer not found' });
    }

    const newTxId = genId('t');
    db.transactions.push({
      id: newTxId,
      customer_id: customerId,
      type: 'payment',
      amount: Number(amount),
      note: note || 'Payment recorded via Mobile App',
      staff_phone: 'mobile_app',
      timestamp: new Date().toISOString(),
      store_id: req.storeId || 'default',
    });

    await writeDB(db);
    // Invalidate cache to ensure AI gets fresh data
    cachedDB = null;
    dbCacheTimestamp = 0;

    const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
    res.json({ success: true, customerName: customer.name, amount: Number(amount), remainingOutstanding: balance });
  } catch (error) {
    console.error('Error adding payment:', error);
    res.status(500).json({ success: false, message: 'Failed to add payment' });
  }
});

// POST /api/bill/create - Create a new bill
app.post('/api/bill/create', async (req, res) => {
  try {
    const { customerId, amount, items } = req.body;
    if (!customerId || !amount) {
      return res.status(400).json({ success: false, message: 'Customer ID and amount are required' });
    }

    const db = await readStoreDB(req.storeId);
    const customer = db.customers.find(c => c.id === customerId);
    if (!customer) {
      return res.status(404).json({ success: false, message: 'Customer not found' });
    }

    const newBillId = genId('b');
    const timestampIso = new Date().toISOString();

    const billItems = items && items.length > 0 
      ? items.map(item => ({
          name: item.name,
          qty: item.qty || 1,
          price: Number(item.price)
        }))
      : [{ name: 'General Grocery Item', qty: 1, price: Number(amount) }];

    // Always calculate total from items (server is source of truth)
    const calculatedTotal = billItems.reduce((sum, item) => sum + (item.price * item.qty), 0);

    db.bills.push({
      id: newBillId,
      customer_id: customerId,
      items: billItems,
      total: calculatedTotal,
      status: 'unpaid',
      created_at: timestampIso,
      paid_at: null,
      store_id: req.storeId || 'default',
    });

    await writeDB(db);
    cachedDB = null;
    dbCacheTimestamp = 0;

    const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
    res.json({ success: true, customerName: customer.name, billId: newBillId, amount: calculatedTotal, netOutstanding: balance });
  } catch (error) {
    console.error('Error creating bill:', error);
    res.status(500).json({ success: false, message: 'Failed to create bill' });
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

    const db = await readStoreDB(req.storeId);
    const bill = db.bills.find(b => b.id === billId);
    if (!bill) {
      return res.status(404).json({ success: false, message: 'Bill not found' });
    }

    if (bill.status === 'paid') {
      return res.json({ success: true, billId, status: 'paid' });
    }

    bill.status = 'paid';
    bill.paid_at = new Date().toISOString();

    await writeDB(db);
    cachedDB = null;
    dbCacheTimestamp = 0;

    return res.json({ success: true, billId, status: 'paid', paid_at: bill.paid_at });
  } catch (error) {
    console.error('Error marking bill paid:', error);
    return res.status(500).json({ success: false, message: 'Failed to mark bill paid' });
  }
});

// ─── REST API ROUTES ───────────────────────────────────────────────────────────

app.get('/api/db', async (req, res) => res.json(await readStoreDB(req.storeId)));

app.post('/api/db', async (req, res) => {
  const body = req.body;
  if (!body || typeof body !== 'object' || !body.customers || !body.transactions || !body.bills) {
    return res.status(400).json({ status: 'error', message: 'Invalid database payload' });
  }
  await writeDB(body);
  res.json({ status: 'success' });
});

// POST /api/register-store - Register a new store
app.post('/api/register-store', async (req, res) => {
  try {
    const { store_name, owner_name, phone, email, business_type, plan, address, password } = req.body;

    if (!store_name || !owner_name || !phone) {
      return res.status(400).json({ status: 'error', message: 'store_name, owner_name, and phone are required' });
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
    const existingStore = db.stores.find(s => s.phone === normalizePhone(phone) || (email && s.email === email));
    if (existingStore) {
      // If a password was provided, update it for existing store
      if (password && password.length >= 4) {
        const newHash = await bcrypt.hash(password, 10);
        const existingStaff = db.staff.find(s => s.phone === normalizePhone(phone) && (s.store_id || 'default') === existingStore.id);
        if (existingStaff) {
          existingStaff.password_hash = newHash;
          await writeDB(db);
        }
      }
      // Return the existing store_id so the client can use it for login
      return res.status(200).json({
        status: 'exists',
        store_id: existingStore.id,
        message: 'Store is already registered with this phone or email'
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
    res.status(500).json({ status: 'error', message: 'Failed to register store' });
  }
});

// GET /api/store/:storeId - Get store information
app.get('/api/store/:storeId', async (req, res) => {
  try {
    const { storeId } = req.params;
    const db = await readDB();
    
    const store = db.stores?.find(s => s.id === storeId);
    
    if (!store) {
      return res.status(404).json({ status: 'error', message: 'Store not found' });
    }
    
    res.json({ status: 'success', store });
  } catch (error) {
    console.error('Error fetching store:', error);
    res.status(500).json({ status: 'error', message: 'Failed to fetch store' });
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
  for (const c of db.customers) {
    const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
    if (bal > 0 && c.phone) {
      const msg =
        `🙏 *${db.shop.name || 'General Store'}*\n\n` +
        `Namaste *${c.name}* ji,\n\n` +
        `Aapka ${fmtRs(bal)} ka baaki hai hamare yahan.\n` +
        `Kripya jald hi chukta karein.\n\nShukriya 🙏`;
      const sent = await sendWhatsAppMessage(c.phone, msg);
      if (sent) { sentCount++; results.push({ name: c.name, amount: bal }); }
    }
  }
  res.json({ status: 'success', sent: sentCount, results });
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
    const pdfUrl = `${baseUrl}/api/bill/${encodeURIComponent(billId)}/pdf`;
    const ok = await sendWhatsAppDocument(
      customer.phone,
      pdfUrl,
      `invoice-${billId}.pdf`,
      `🧾 Invoice from ${shop.name || 'Store'}`
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
    const pdfUrl = `${baseUrl}/api/customer/${encodeURIComponent(customerId)}/statement/pdf`;
    const ok = await sendWhatsAppDocument(
      customer.phone,
      pdfUrl,
      `statement-${customerId}.pdf`,
      `📊 Account statement from ${shop.name || 'Store'}`
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
    const text =
      (message && String(message).trim()) ||
      `🙏 *${shop.name || 'Store'}*\n\nNamaste *${customer.name}* ji,\nAapka ${fmtRs(bal)} ka baaki hai.\nKripya jald hi chukta karein.`;

    const ok = await sendWhatsAppMessage(customer.phone, text);
    return res.json({ success: ok, customerId, customerPhone: customer.phone, outstanding: bal });
  } catch (error) {
    console.error('Error sending reminder:', error);
    return res.status(500).json({ success: false, message: 'Failed to send reminder' });
  }
});

// GET /api/bill/:id/pdf — Generate and return PDF invoice
app.get('/api/bill/:id/pdf', async (req, res) => {
  try {
    const db = await readDB();
    const bill = db.bills.find(b => b.id === req.params.id);
    const shop = db.shop || {};

    if (!bill) {
      return res.status(404).json({ status: 'error', message: 'Bill not found' });
    }

    const customer = db.customers.find(c => c.id === bill.customer_id) || {
      name: 'Walk-in Customer',
      phone: '0000000000'
    };

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
    res.setHeader('Content-Disposition', `attachment; filename=invoice-${bill.id}.pdf`);
    doc.pipe(res);

    const pageWidth = 595; // A4 width
    const margin = 40;
    const contentWidth = pageWidth - (margin * 2);

    // ── HEADER BANNER ────────────────────────────────────────
    doc.rect(0, 0, pageWidth, 110).fill(INDIGO);
    doc.fontSize(24).fillColor('#FFFFFF')
       .text(shop.name || 'GENERAL STORE', margin, 25, { width: contentWidth, align: 'center' });
    doc.fontSize(10).fillColor('#C7D2FE')
       .text(shop.address || '', margin, 55, { width: contentWidth, align: 'center' });
    if (shop.phone) {
      doc.text(`WhatsApp: +91 ${shop.phone}`, margin, 70, { width: contentWidth, align: 'center' });
    }
    doc.fontSize(9).fillColor('#A5B4FC')
       .text('TAX INVOICE', margin, 90, { width: contentWidth, align: 'center' });

    // ── INVOICE META (right-aligned) ─────────────────────────
    let y = 130;
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

    // Subtotal
    doc.fontSize(10).fillColor(SLATE_600).text('Subtotal', totalsX, y, { width: 120, align: 'left' });
    doc.fontSize(10).fillColor(SLATE_800).text(`₹${bill.total.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, totalsX + 120, y, { width: 95, align: 'right' });
    y += 22;

    // Grand Total box
    doc.roundedRect(totalsX - 10, y, totalsW + 20, 36, 6).fill(INDIGO_LIGHT);
    doc.fontSize(12).fillColor(INDIGO_DARK).text('Grand Total', totalsX, y + 10, { width: 120, align: 'left' });
    doc.fontSize(16).fillColor(INDIGO).text(`₹${bill.total.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, totalsX + 100, y + 8, { width: 115, align: 'right' });

    y += 55;

    // ── OUTSTANDING ──────────────────────────────────────────
    const outstanding = getCustomerOutstanding(customer.id, db.transactions || [], db.bills || []);
    if (outstanding !== bill.total || bill.status === 'paid') {
      doc.fontSize(9).fillColor(SLATE_400).text('Total Outstanding (incl. previous):', margin, y);
      const outColor = outstanding > 0 ? RED : GREEN;
      doc.fontSize(11).fillColor(outColor).text(`₹${Math.abs(outstanding).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, margin, y + 14);
      y += 35;
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

  } catch (error) {
    console.error('Error generating PDF:', error);
    res.status(500).json({ status: 'error', message: 'Failed to generate PDF' });
  }
});

// GET /api/customer/:id/statement/pdf — Generate customer statement PDF
app.get('/api/customer/:id/statement/pdf', async (req, res) => {
  try {
    const db = await readDB();
    const customer = db.customers.find(c => c.id === req.params.id);
    const shop = db.shop || {};

    if (!customer) {
      return res.status(404).json({ status: 'error', message: 'Customer not found' });
    }

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

  } catch (error) {
    console.error('Error generating statement PDF:', error);
    res.status(500).json({ status: 'error', message: 'Failed to generate statement PDF' });
  }
});

// GET /api/report/:date/pdf — Generate daily report PDF
app.get('/api/report/:date/pdf', async (req, res) => {
  try {
    const targetDate = req.params.date;
    const db = await readDB();
    const shop = db.shop || {};

    const billsToday = db.bills.filter(b => b.created_at.startsWith(targetDate));
    const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
    const collectionsToday = db.transactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(targetDate));
    const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);
    const creditsToday = db.transactions.filter(t => t.type === 'credit' && t.timestamp.startsWith(targetDate));
    const creditTotal = creditsToday.reduce((sum, t) => sum + t.amount, 0);
    const paidBills = billsToday.filter(b => b.status === 'paid').length;
    const unpaidBills = billsToday.length - paidBills;
    const netCollection = paymentTotal - creditTotal;

    // Overall outstanding
    const totalOutstanding = db.customers.reduce((sum, c) => {
      const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
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
        const cust = db.customers.find(c => c.id === bill.customerId);
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

  } catch (error) {
    console.error('Error generating report PDF:', error);
    res.status(500).json({ status: 'error', message: 'Failed to generate report PDF' });
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

app.listen(PORT, async () => {
  console.log(`🚀 Store Bot running on port ${PORT}`);
  console.log(`📱 Webhook: POST /webhook`);
  console.log(`🌐 Dashboard: http://localhost:${PORT}`);
  console.log(`🌐 Dashboard API: GET /api/db`);
  await migratePhoneNumbers();
  // Start daily 9 AM report scheduler
  scheduleDaily(9, 0, sendDailyReport);
});
