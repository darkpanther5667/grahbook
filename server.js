require('dotenv').config();
const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const OpenAI = require('openai');
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

// ─── DATABASE HELPERS (MONGODB) ────────────────────────────────────────────────
const { connectDB, getFullDB } = require('./db.js');

let cachedDB = { shop: {}, customers: [], transactions: [], bills: [], staff: [] };
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
    // Overwrite collections (mimics the db.json full overwrite behavior for backwards compatibility)
    for (const col of ['shop', 'customers', 'transactions', 'bills', 'staff']) {
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

async function addCustomerTool(name, phone) {
  const db = await readDB();
  const existing = db.customers.find(c => c.phone === phone || c.name.toLowerCase() === name.toLowerCase());
  if (existing) {
    return { error: `Customer '${name}' or phone '${phone}' is already registered.` };
  }
  const newCustomer = {
    id: genId('c'),
    name: name.replace(/\b\w/g, c => c.toUpperCase()),
    phone,
    created_at: new Date().toISOString().substring(0, 10)
  };
  db.customers.push(newCustomer);
  await writeDB(db);
  // Invalidate cache to ensure AI gets fresh data
  cachedDB = null;
  dbCacheTimestamp = 0;
  return { success: true, customer: newCustomer };
}

async function recordPaymentTool(customerId, amount, note, staffPhone) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
  const newTxId = genId('t');
  db.transactions.push({
    id: newTxId,
    customer_id: customerId,
    type: 'payment',
    amount: Number(amount),
    note: note || 'Payment recorded via AI Bot',
    staff_phone: staffPhone || 'system',
    timestamp: new Date().toISOString()
  });
  await writeDB(db);
  // Invalidate cache to ensure AI gets fresh data
  cachedDB = null;
  dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, amount: Number(amount), remainingOutstanding: balance };
}

async function addCreditTool(customerId, amount, note, staffPhone) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
  const newTxId = genId('t');
  db.transactions.push({
    id: newTxId,
    customer_id: customerId,
    type: 'credit',
    amount: Number(amount),
    note: note || 'Credit added via AI Bot',
    staff_phone: staffPhone || 'system',
    timestamp: new Date().toISOString()
  });
  await writeDB(db);
  // Invalidate cache to ensure AI gets fresh data
  cachedDB = null;
  dbCacheTimestamp = 0;
  const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
  return { success: true, customerName: customer.name, amountAdded: Number(amount), totalOutstanding: balance };
}

async function addItemToUnpaidBillTool(customerId, itemName, price, qty) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
  let currentBill = db.bills.find(b => b.customer_id === customerId && b.status === 'unpaid');
  const timestampIso = new Date().toISOString();
  if (!currentBill) {
    currentBill = {
      id: genId('b'),
      customer_id: customerId,
      items: [],
      total: 0,
      status: 'unpaid',
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

async function generateBillTool(customerId, amount) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
  const newBillId = genId('b');
  const timestampIso = new Date().toISOString();
  db.bills.push({
    id: newBillId,
    customer_id: customerId,
    items: [{ name: 'General Grocery Item', qty: 1, price: Number(amount) }],
    total: Number(amount),
    status: 'unpaid',
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

async function markBillAsPaidTool(customerId) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };
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

async function getCustomerBalancesTool() {
  const db = await readDB();
  const balances = db.customers.map(c => {
    const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
    return { id: c.id, name: c.name, phone: c.phone, outstandingBalance: bal };
  });
  return { success: true, balances };
}

async function getBillPdfTool(customerId) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };

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

async function getCustomerStatementPdfTool(customerId) {
  const db = await readDB();
  const customer = db.customers.find(c => c.id === customerId);
  if (!customer) return { error: `Customer with ID '${customerId}' not found.` };

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

async function getDailyReportPdfTool(date) {
  const db = await readDB();
  const targetDate = date || new Date().toISOString().substring(0, 10);
  
  const billsToday = db.bills.filter(b => b.created_at.startsWith(targetDate));
  const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
  const collectionsToday = db.transactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(targetDate));
  const paymentTotal = collectionsToday.reduce((sum, t) => sum + t.amount, 0);
  const creditsToday = db.transactions.filter(t => t.type === 'credit' && t.timestamp.startsWith(targetDate));
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

async function getTodaySalesReportTool() {
  const db = await readDB();
  const todayString = new Date().toISOString().substring(0, 10);
  const billsToday = db.bills.filter(b => b.created_at.startsWith(todayString));
  const billsTotal = billsToday.reduce((sum, b) => sum + b.total, 0);
  const collectionsToday = db.transactions.filter(t => t.type === 'payment' && t.timestamp.startsWith(todayString));
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

async function getShopDetailsTool() {
  const db = await readDB();
  return { success: true, shop: db.shop || {} };
}

async function getCustomersListTool() {
  const db = await readDB();
  const list = db.customers.map(c => ({ id: c.id, name: c.name, phone: c.phone }));
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
  // e.g. "naya customer Rahul 9876543210" / "add customer Priya 98765"
  const newCustMatch = text.match(/(?:naya|new|add)\s+(?:customer|khata|grahak)\s+([a-zA-Z\u0900-\u097F\s]+?)\s+(\d{10})/i);
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
  if (text.includes('bill bhej') || text.includes('send bill') || text.includes('bill send')) {
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
  // e.g. "Ramesh ne 200 diya" / "Ramesh ne 500 rupaye diye" / "Ramesh payment 300"
  const paymentMatch = text.match(/(?:ne|ka|ke)?\s*(\d+)\s*(?:diya|diye|rupaye|rs|payment|jama|ada|paid)/i) ||
    text.match(/payment\s+(\d+)/i) || text.match(/(\d+)\s*(?:diya|diye|ada kiya)/i);
  if (paymentMatch && (text.includes('ne ') || text.includes('payment') || text.includes('diya') || text.includes('diye') || text.includes('ada'))) {
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
    /(?:(?:ka|ke)\s+(\d+)\s*(?:mein|me)?\s*([a-zA-Z\u0900-\u097F]+?)\s*add\s*(?:karo|do)?)|(?:(?:ka|ke)\s*([a-zA-Z\u0900-\u097F]+?)\s+(\d+)\s*add\s*(?:karo|do)?)/
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
async function executeTool(name, args, staffPhone) {
  console.log(`🛠️ Executing AI Tool: "${name}" with args:`, args);
  try {
    switch (name) {
      case 'addCustomerTool':
        return await addCustomerTool(args.name, args.phone);
      case 'recordPaymentTool':
        return await recordPaymentTool(args.customerId, args.amount, args.note, staffPhone);
      case 'addCreditTool':
        return await addCreditTool(args.customerId, args.amount, args.note, staffPhone);
      case 'addItemToUnpaidBillTool':
        return await addItemToUnpaidBillTool(args.customerId, args.itemName, args.price, args.qty);
      case 'generateBillTool':
        return await generateBillTool(args.customerId, args.amount);
      case 'markBillAsPaidTool':
        return await markBillAsPaidTool(args.customerId);
      case 'getCustomerBalancesTool':
        return await getCustomerBalancesTool();
      case 'getTodaySalesReportTool':
        return await getTodaySalesReportTool();
      case 'getShopDetailsTool':
        return await getShopDetailsTool();
      case 'getCustomersListTool':
        return await getCustomersListTool();
      case 'getBillPdfTool':
        return await getBillPdfTool(args.customerId);
      case 'getCustomerStatementPdfTool':
        return await getCustomerStatementPdfTool(args.customerId);
      case 'getDailyReportPdfTool':
        return await getDailyReportPdfTool(args.date);
      default:
        return { error: `Tool "${name}" is not implemented.` };
    }
  } catch (err) {
    console.error(`❌ Error executing tool "${name}":`, err);
    return { error: err.message };
  }
}

async function askOpenRouterWithTools(messageText, staffPhone) {
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

  const modelName = process.env.OPENROUTER_MODEL || 'openai/gpt-4o-mini';
  const language = getLanguage(staffPhone);
  const conversationContext = getConversationContext(staffPhone);
  
  try {
    const db = await readDB();
    const shopInfo = db.shop || {};
    
    // Declare the database tools for OpenRouter (OpenAI format)
    const tools = [
      {
        type: 'function',
        function: {
          name: 'addCustomerTool',
          description: 'Registers a new customer in the database with their name and phone number. Returns success:true with customer object if successful, or error if customer already exists.',
          parameters: {
            type: 'object',
            properties: {
              name: { type: 'string', description: 'The customer\'s full name, e.g. "Rahul Singh".' },
              phone: { type: 'string', description: 'The customer\'s 10-digit phone number, e.g. "9876543210".' }
            },
            required: ['name', 'phone']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'recordPaymentTool',
          description: 'Records a payment (jama/diya/cash received) from an existing customer to reduce their outstanding balance.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' },
              amount: { type: 'number', description: 'The payment amount in Rupees, e.g. 500.' },
              note: { type: 'string', description: 'Optional description of the payment, e.g. "Cash token payment".' }
            },
            required: ['customerId', 'amount']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'addCreditTool',
          description: 'Adds outstanding credit (udhaar/khata/item taken on credit) to an existing customer.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' },
              amount: { type: 'number', description: 'The credit amount in Rupees, e.g. 150.' },
              note: { type: 'string', description: 'Optional description of the credit, e.g. "Refined oil 2L".' }
            },
            required: ['customerId', 'amount']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'addItemToUnpaidBillTool',
          description: 'Adds an item (along with price and optional quantity) to a customer\'s active unpaid bill. Creates a new unpaid bill if none exists.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' },
              itemName: { type: 'string', description: 'The name of the item, e.g. "Sugar".' },
              price: { type: 'number', description: 'The price per unit in Rupees, e.g. 50.' },
              qty: { type: 'number', description: 'The quantity of items, defaults to 1.' }
            },
            required: ['customerId', 'itemName', 'price']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'generateBillTool',
          description: 'Generates a simple fixed-amount unpaid bill for a customer.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' },
              amount: { type: 'number', description: 'The total bill amount in Rupees, e.g. 1000.' }
            },
            required: ['customerId', 'amount']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'markBillAsPaidTool',
          description: 'Marks the customer\'s active unpaid bill as paid.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' }
            },
            required: ['customerId']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getCustomerBalancesTool',
          description: 'Retrieves outstanding balances for all registered customers.',
          parameters: { type: 'object', properties: {} }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getTodaySalesReportTool',
          description: 'Retrieves today\'s sales report including total sales, total collections, and count of bills.',
          parameters: { type: 'object', properties: {} }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getShopDetailsTool',
          description: 'Retrieves metadata about the shop, such as shop name, owner, and address.',
          parameters: { type: 'object', properties: {} }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getCustomersListTool',
          description: 'Retrieves a list of all registered customers with their IDs, names, and phone numbers.',
          parameters: { type: 'object', properties: {} }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getBillPdfTool',
          description: 'Generates a PDF invoice for a customer and returns the download URL. Finds the latest unpaid bill or creates a simple one if none exists.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' }
            },
            required: ['customerId']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getCustomerStatementPdfTool',
          description: 'Generates a PDF statement for a customer showing all their transactions, bills, and current balance.',
          parameters: {
            type: 'object',
            properties: {
              customerId: { type: 'string', description: 'The customer ID, e.g. "c1".' }
            },
            required: ['customerId']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getDailyReportPdfTool',
          description: 'Generates a PDF daily report showing sales, collections, credits, and bill counts for a specific date.',
          parameters: {
            type: 'object',
            properties: {
              date: { type: 'string', description: 'The date in YYYY-MM-DD format. If not provided, uses today\'s date.' }
            },
            required: []
          }
        }
      }
    ];

    // Build language-specific system instruction
    const languageInstruction = language === 'english' 
      ? `You must respond in English only. Do not use Hindi or Hinglish.`
      : `You must respond in Hindi or Hinglish only. Do not use English. Use natural conversational Hindi that a shopkeeper would use.`;

    const systemInstruction = `
You are the AI assistant for ${shopInfo.name || 'Sharma General Store'} owned by ${shopInfo.owner || 'Rajesh Sharma'} located at ${shopInfo.address || 'Main Bazaar, Farrukhabad'}.
You process incoming messages from store staff and perform operations on the database using the tools available.

${languageInstruction}

CRITICAL RULES TO PREVENT HALLUCINATION:
1. ALWAYS start by fetching the list of customers using 'getCustomersListTool' if you need to map names to customer IDs. If the user mentions customer names (e.g. "ramesh", "suresh"), you MUST match them to the registered customer ID from the list.
2. If a customer is mentioned but is NOT in the list, tell the user that the customer is not registered, and politely instruct them to register the customer first using the format 'naya customer <naam> <phone>'. Do NOT call any other tool for that customer.
3. NEVER make up customer IDs, names, or data. Only use data returned from tools. If addCustomerTool returns success, use the EXACT customer ID it returns. Do not invent your own.
4. If money/rupees are mentioned, pass them to tools as integers/numbers.
5. Execute tools when needed to resolve the user's intent. You can make multiple tool calls in a single turn (e.g., adding a customer and then recording their payment).
6. After receiving the output of a tool, summarize the result in a polite conversational message to send back to the user. Make sure to use emojis, clear formatting, and standard Indian currency style (e.g., ₹1,000).
7. If the request is a general question (greeting, shop details, or balance questions), call the appropriate query tool ('getShopDetailsTool', 'getCustomerBalancesTool', etc.) to fetch accurate info before answering.
8. If a tool returns an error, communicate the exact error message to the user. Do not make up success messages.
9. Keep responses concise and relevant. Do not add unnecessary information.
10. PAY ATTENTION to conversation context. If the user previously provided a customer ID or name, remember it and use it. Don't ask for it again.
11. If user provides a customer ID in format "(ID: c_xxxxx)" or "ID: c_xxxxx", use that exact ID for operations.
`;

    // Build conversation context string
    let contextString = '';
    let lastCustomerId = null;
    
    if (conversationContext.length > 0) {
      contextString = '\n\nRecent conversation context:\n';
      conversationContext.forEach((ctx, idx) => {
        contextString += `${idx + 1}. User: "${ctx.message}"\n   Assistant: "${ctx.response.substring(0, 100)}..."\n`;
        
        // Extract customer ID from recent messages
        const extractedId = extractCustomerId(ctx.message);
        if (extractedId) {
          lastCustomerId = extractedId;
        }
      });
      
      if (lastCustomerId) {
        contextString += `\n⚠️ IMPORTANT: User recently mentioned customer ID: ${lastCustomerId}. Use this ID if they ask for operations without specifying customer again.`;
      }
    }

    // Initialize messages list with context
    const messages = [
      { role: 'system', content: `${systemInstruction}${contextString}` },
      { role: 'user', content: messageText }
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
        const toolResult = await executeTool(toolCall.function.name, JSON.parse(toolCall.function.arguments), staffPhone);
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
    const errorMsg = language === 'english'
      ? `❌ Technical error occurred. Please try again or contact admin. 🙏`
      : `❌ कुछ तकनीकल समस्या हुई। कृपया दोबारा कोशिश करें या एडमिन को बताएं। 🙏`;
    
    if (error.status === 429) {
      return language === 'english'
        ? `⏳ Bot is busy right now. Please try again in a minute. 🙏`
        : `⏳ बॉट अभी व्यस्त है। एक मिनट बाद दोबारा कोशिश करें। 🙏`;
    }
    if (error.status === 503 || error.message?.includes('network')) {
      return language === 'english'
        ? `📶 Network error. Please try again later.`
        : `📶 नेटवर्क एरर। कृपया बाद में कोशिश करें।`;
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

    const db = await readDB();
    const activeStaff = db.staff.find(s => s.phone === staffPhone) || { id: 'unknown', name: 'Unknown User' };
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
      const existing = db.customers.find(c => c.phone === phone || c.name.toLowerCase() === name.toLowerCase());
      if (existing) {
        replyText = `⚠️ *${name}* ya phone *${phone}* se ek customer pehle se registered hai.`;
      } else {
        const newCustomer = {
          id: genId('c'),
          name,
          phone,
          created_at: timestampIso.substring(0, 10)
        };
        db.customers.push(newCustomer);
        await writeDB(db);
        // Invalidate cache to ensure AI gets fresh data
        cachedDB = null;
        dbCacheTimestamp = 0;
        replyText =
          `✅ *Naya Customer Add Ho Gaya!*\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `👤 Naam: *${name}*\n` +
          `📱 Phone: ${phone}\n` +
          `🆔 ID: ${newCustomer.id}\n` +
          `📅 Registered: ${fmtDate(timestampIso)}\n` +
          `━━━━━━━━━━━━━━━━━━━━\n` +
          `_Ab aap iske liye bill ya khata bana sakte hain._`;
      }

    // ── RECORD PAYMENT ───────────────────────────────────────────────────────────
    } else if (action.type === 'record_payment') {
      const { customerId, customerName, amount } = action;
      const newTxId = genId('t');
      db.transactions.push({
        id: newTxId,
        customer_id: customerId,
        type: 'payment',
        amount,
        note: `Payment received via Bot by ${activeStaff.name}`,
        staff_phone: staffPhone,
        timestamp: timestampIso
      });
      await writeDB(db);
      // Invalidate cache to ensure AI gets fresh data
      cachedDB = null;
      dbCacheTimestamp = 0;
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
      db.transactions.push({
        id: newTxId,
        customer_id: customerId,
        type: 'credit',
        amount,
        note: `Credit added via Bot by ${activeStaff.name}`,
        staff_phone: staffPhone,
        timestamp: timestampIso
      });
      await writeDB(db);
      // Invalidate cache to ensure AI gets fresh data
      cachedDB = null;
      dbCacheTimestamp = 0;
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
      db.bills.push({
        id: newBillId,
        customer_id: customerId,
        items: [{ name: 'General Grocery Item', qty: 1, price: amount }],
        total: amount,
        status: 'unpaid',
        created_at: timestampIso,
        paid_at: null
      });
      await writeDB(db);
      // Invalidate cache to ensure AI gets fresh data
      cachedDB = null;
      dbCacheTimestamp = 0;
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
          created_at: timestampIso,
          paid_at: null
        };
        db.bills.push(currentBill);
      }
      currentBill.items.push({ name: itemName, qty: 1, price });
      currentBill.total += price;
      await writeDB(db);
      // Invalidate cache to ensure AI gets fresh data
      cachedDB = null;
      dbCacheTimestamp = 0;
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
        await writeDB(db);
        // Invalidate cache to ensure AI gets fresh data
        cachedDB = null;
        dbCacheTimestamp = 0;
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
      replyText = await askOpenRouterWithTools(bodyText, staffPhone);
      console.log(`🤖 AI Agent reply: "${replyText}"`);
    }

    await sendWhatsAppMessage(staffPhone, replyText);
    return res.status(200).json({ status: 'success', action: action.type, aiUsed: action.type === 'unknown' });
  }

  return res.sendStatus(200);
});

// ─── MOBILE APP API ENDPOINTS ─────────────────────────────────────────────────────

// POST /api/customer/add - Add a new customer
app.post('/api/customer/add', async (req, res) => {
  try {
    const { name, phone } = req.body;
    if (!name || !phone) {
      return res.status(400).json({ success: false, message: 'Name and phone are required' });
    }

    const db = await readDB();
    
    // Check if customer already exists
    const existingCustomer = db.customers.find(c => c.name.toLowerCase() === name.toLowerCase() || c.phone === phone);
    if (existingCustomer) {
      return res.status(400).json({ success: false, message: 'Customer already exists' });
    }

    const newCustomer = {
      id: genId('c'),
      name: name.replace(/\b\w/g, c => c.toUpperCase()),
      phone,
      created_at: new Date().toISOString().substring(0, 10)
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

    const db = await readDB();
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
      timestamp: new Date().toISOString()
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

    const db = await readDB();
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

    db.bills.push({
      id: newBillId,
      customer_id: customerId,
      items: billItems,
      total: Number(amount),
      status: 'unpaid',
      created_at: timestampIso,
      paid_at: null
    });

    await writeDB(db);
    // Invalidate cache to ensure AI gets fresh data
    cachedDB = null;
    dbCacheTimestamp = 0;

    const balance = getCustomerOutstanding(customerId, db.transactions, db.bills);
    res.json({ success: true, customerName: customer.name, billId: newBillId, amount: Number(amount), netOutstanding: balance });
  } catch (error) {
    console.error('Error creating bill:', error);
    res.status(500).json({ success: false, message: 'Failed to create bill' });
  }
});

// ─── REST API ROUTES ───────────────────────────────────────────────────────────

app.get('/api/db', async (req, res) => res.json(await readDB()));

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
    const { store_name, owner_name, phone, email, business_type, plan } = req.body;
    
    if (!store_name || !owner_name || !phone || !email) {
      return res.status(400).json({ status: 'error', message: 'Missing required fields' });
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
    const existingStore = db.stores.find(s => s.phone === phone || s.email === email);
    if (existingStore) {
      return res.status(400).json({ status: 'error', message: 'Store already registered with this phone or email' });
    }
    
    // Create new store
    const newStore = {
      id: storeId,
      store_name: store_name,
      owner_name: owner_name,
      phone: phone,
      email: email,
      business_type: business_type || 'retail',
      plan: plan || 'basic',
      created_at: new Date().toISOString(),
      status: 'active'
    };
    
    // Add store to database
    db.stores.push(newStore);
    
    // Write updated database
    await writeDB(db);
    
    // Send welcome message via WhatsApp
    const welcomeMessage = `🎉 *Welcome to Grahbook!* 🎉\n\nYour store "${store_name}" has been successfully registered.\n\n📱 *Your Store Dashboard Link:* https://grahbook.com/dashboard/${storeId}\n\nUse this link to access your personalized dashboard.\n\nIf you have any questions, feel free to reach out to our support team.\n\nHappy selling! 🚀`;
    await sendWhatsAppMessage(phone, welcomeMessage);
    
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
  const db = await readDB();
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

    // Create PDF document
    const doc = new PDFDocument({ size: 'A4', margin: 50 });

    // Set response headers for PDF download
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=invoice-${bill.id}.pdf`);

    // Pipe PDF directly to response
    doc.pipe(res);

    // Header
    doc.fontSize(20).text(shop.name || 'GENERAL STORE', { align: 'center' });
    doc.fontSize(12).text(shop.address || '', { align: 'center' });
    if (shop.phone) {
      doc.text(`WhatsApp Contact: +91 ${shop.phone}`, { align: 'center' });
    }
    doc.moveDown(2);

    // Invoice details
    doc.fontSize(14).text(`Invoice ID: ${bill.id}`, { align: 'right' });
    doc.text(`Date: ${new Date(bill.created_at).toLocaleDateString('en-GB')}`, { align: 'right' });
    doc.moveDown(2);

    // Customer info
    doc.fontSize(12).text('Bill To:', { underline: true });
    doc.text(`${customer.name}`);
    doc.text(`Phone: +91 ${customer.phone}`);
    doc.moveDown(2);

    // Items table header
    doc.fontSize(12).text('Description', 50, doc.y, { width: 200, align: 'left' });
    doc.text('Qty', 300, doc.y, { width: 50, align: 'center' });
    doc.text('Price (₹)', 380, doc.y, { width: 80, align: 'right' });
    doc.text('Total (₹)', 480, doc.y, { width: 80, align: 'right' });
    doc.moveDown(0.5);
    doc.strokeColor('#cccccc').lineWidth(1).lineCap('butt').moveTo(50, doc.y).lineTo(580, doc.y).stroke();
    doc.moveDown(0.5);

    // Items table rows
    bill.items.forEach((item, index) => {
      const itemTotal = item.price * item.qty;
      doc.fontSize(11).text(`${index + 1}. ${item.name}`, 50, doc.y, { width: 200, align: 'left' });
      doc.text(item.qty.toString(), 300, doc.y, { width: 50, align: 'center' });
      doc.text(`₹${item.price.toFixed(2)}`, 380, doc.y, { width: 80, align: 'right' });
      doc.text(`₹${itemTotal.toFixed(2)}`, 480, doc.y, { width: 80, align: 'right' });
      doc.moveDown(1.2);
    });

    doc.moveDown(1);
    doc.strokeColor('#cccccc').lineWidth(1).lineCap('butt').moveTo(50, doc.y).lineTo(580, doc.y).stroke();
    doc.moveDown(1);

    // Totals
    doc.fontSize(12).text('Subtotal:', 400, doc.y, { width: 100, align: 'right' });
    doc.text(`₹${bill.total.toFixed(2)}`, 500, doc.y, { width: 80, align: 'right' });
    doc.moveDown(0.8);
    doc.text('Discount/Tax:', 400, doc.y, { width: 100, align: 'right' });
    doc.text('₹0.00', 500, doc.y, { width: 80, align: 'right' });
    doc.moveDown(0.8);
    doc.fontSize(14).text('Grand Total:', 400, doc.y, { width: 100, align: 'right' });
    doc.text(`₹${bill.total.toFixed(2)}`, 500, doc.y, { width: 80, align: 'right' });
    doc.moveDown(2);

    // Status
    const statusText = bill.status === 'paid' ? 'PAID (जमा)' : 'UNPAID (बाकी)';
    const statusColor = bill.status === 'paid' ? 'green' : 'red';
    doc.fontSize(12).fillColor(statusColor).text(`Status: ${statusText}`, { align: 'right' });
    doc.fillColor('black'); // Reset color

    // Footer
    doc.moveDown(3);
    doc.fontSize(10).text('Thank you for shopping! 🙏', { align: 'center' });
    doc.text('This is a digital system generated invoice.', { align: 'center' });
    doc.text('For any query, refer to our WhatsApp store bot message verification.', { align: 'center' });

    // Finalize PDF
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

    // Create PDF document
    const doc = new PDFDocument({ size: 'A4', margin: 50 });

    // Set response headers for PDF download
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=statement-${customer.name.replace(/\s+/g, '_')}.pdf`);

    // Pipe PDF directly to response
    doc.pipe(res);

    // Header
    doc.fontSize(20).text(shop.name || 'GENERAL STORE', { align: 'center' });
    doc.fontSize(12).text(shop.address || '', { align: 'center' });
    if (shop.phone) {
      doc.text(`WhatsApp Contact: +91 ${shop.phone}`, { align: 'center' });
    }
    doc.moveDown(2);

    // Statement title
    doc.fontSize(16).text('CUSTOMER STATEMENT', { align: 'center' });
    doc.moveDown(1);

    // Customer info
    doc.fontSize(12).text('Customer Details:', { underline: true });
    doc.text(`Name: ${customer.name}`);
    doc.text(`Phone: +91 ${customer.phone}`);
    doc.text(`Customer ID: ${customer.id}`);
    doc.moveDown(1);

    // Balance summary
    doc.fontSize(12).text('Account Summary:', { underline: true });
    const balanceColor = balance > 0 ? 'red' : (balance < 0 ? 'green' : 'black');
    doc.fillColor(balanceColor).text(`Current Outstanding: ${fmtRs(balance)}`);
    doc.fillColor('black');
    doc.text(`Total Transactions: ${customerTransactions.length}`);
    doc.text(`Total Bills: ${customerBills.length}`);
    doc.moveDown(2);

    // Transactions section
    if (customerTransactions.length > 0) {
      doc.fontSize(14).text('Transaction History', { underline: true });
      doc.moveDown(0.5);

      // Table header
      doc.fontSize(10).text('Date', 50, doc.y, { width: 80, align: 'left' });
      doc.text('Type', 150, doc.y, { width: 60, align: 'left' });
      doc.text('Amount (₹)', 230, doc.y, { width: 80, align: 'right' });
      doc.text('Note', 330, doc.y, { width: 200, align: 'left' });
      doc.moveDown(0.5);
      doc.strokeColor('#cccccc').lineWidth(1).moveTo(50, doc.y).lineTo(580, doc.y).stroke();
      doc.moveDown(0.5);

      // Transaction rows
      customerTransactions.forEach((tx, index) => {
        const typeColor = tx.type === 'payment' ? 'green' : 'red';
        doc.fillColor('black').text(fmtDate(tx.timestamp), 50, doc.y, { width: 80, align: 'left' });
        doc.fillColor(typeColor).text(tx.type.toUpperCase(), 150, doc.y, { width: 60, align: 'left' });
        doc.fillColor('black').text(`₹${tx.amount.toFixed(2)}`, 230, doc.y, { width: 80, align: 'right' });
        doc.text(tx.note || '-', 330, doc.y, { width: 200, align: 'left' });
        doc.fillColor('black');
        doc.moveDown(0.8);
      });

      doc.moveDown(1);
      doc.strokeColor('#cccccc').lineWidth(1).moveTo(50, doc.y).lineTo(580, doc.y).stroke();
      doc.moveDown(1);
    }

    // Bills section
    if (customerBills.length > 0) {
      doc.fontSize(14).text('Bill History', { underline: true });
      doc.moveDown(0.5);

      customerBills.forEach((bill, index) => {
        const statusColor = bill.status === 'paid' ? 'green' : 'red';
        doc.fontSize(10).fillColor('black').text(`Bill #${bill.id}`, 50, doc.y);
        doc.text(`Date: ${fmtDate(bill.created_at)}`, 150, doc.y);
        doc.fillColor(statusColor).text(`Status: ${bill.status.toUpperCase()}`, 350, doc.y);
        doc.fillColor('black').text(`Total: ${fmtRs(bill.total)}`, 480, doc.y);
        doc.moveDown(0.8);
      });
    }

    // Footer
    doc.moveDown(3);
    doc.fontSize(10).text('This is a computer-generated statement.', { align: 'center' });
    doc.text('For any discrepancies, please contact the store.', { align: 'center' });
    doc.text('Generated on: ' + new Date().toLocaleString('en-IN'), { align: 'center' });

    // Finalize PDF
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

    // Create PDF document
    const doc = new PDFDocument({ size: 'A4', margin: 50 });

    // Set response headers for PDF download
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=daily-report-${targetDate}.pdf`);

    // Pipe PDF directly to response
    doc.pipe(res);

    // Header
    doc.fontSize(20).text(shop.name || 'GENERAL STORE', { align: 'center' });
    doc.fontSize(12).text(shop.address || '', { align: 'center' });
    if (shop.phone) {
      doc.text(`WhatsApp Contact: +91 ${shop.phone}`, { align: 'center' });
    }
    doc.moveDown(2);

    // Report title
    doc.fontSize(16).text('DAILY SALES REPORT', { align: 'center' });
    doc.moveDown(1);

    // Date
    doc.fontSize(12).text(`Report Date: ${fmtDate(targetDate)}`, { align: 'center' });
    doc.moveDown(2);

    // Summary section
    doc.fontSize(14).text('Summary', { underline: true });
    doc.moveDown(1);

    doc.fontSize(12).text('Total Sales (Bills):', 50, doc.y);
    doc.text(fmtRs(billsTotal), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(0.8);

    doc.text('Total Collections:', 50, doc.y);
    doc.text(fmtRs(paymentTotal), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(0.8);

    doc.text('Total Credits Given:', 50, doc.y);
    doc.text(fmtRs(creditTotal), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(0.8);

    doc.text('Net Collection:', 50, doc.y);
    doc.text(fmtRs(paymentTotal - creditTotal), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(1.5);

    // Bills section
    doc.fontSize(14).text('Bills Summary', { underline: true });
    doc.moveDown(0.8);

    doc.fontSize(12).text('Total Bills Created:', 50, doc.y);
    doc.text(billsToday.length.toString(), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(0.8);

    doc.text('Bills Paid:', 50, doc.y);
    doc.text(paidBills.toString(), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(0.8);

    doc.text('Bills Unpaid:', 50, doc.y);
    doc.text((billsToday.length - paidBills).toString(), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(1.5);

    // Outstanding summary
    const totalOutstanding = db.customers.reduce((sum, c) => {
      const bal = getCustomerOutstanding(c.id, db.transactions, db.bills);
      return sum + (bal > 0 ? bal : 0);
    }, 0);

    doc.fontSize(14).text('Overall Outstanding', { underline: true });
    doc.moveDown(0.8);

    doc.fontSize(12).text('Total Outstanding (All Customers):', 50, doc.y);
    doc.text(fmtRs(totalOutstanding), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(0.8);

    doc.text('Total Customers:', 50, doc.y);
    doc.text(db.customers.length.toString(), 400, doc.y, { width: 100, align: 'right' });
    doc.moveDown(2);

    // Footer
    doc.fontSize(10).text('This is a computer-generated report.', { align: 'center' });
    doc.text('Generated on: ' + new Date().toLocaleString('en-IN'), { align: 'center' });

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

app.listen(PORT, () => {
  console.log(`🚀 Store Bot running on port ${PORT}`);
  console.log(`📱 Webhook: POST /webhook`);
  console.log(`🌐 Dashboard: http://localhost:${PORT}`);
  console.log(`🌐 Dashboard API: GET /api/db`);
  // Start daily 9 AM report scheduler
  scheduleDaily(9, 0, sendDailyReport);
});
