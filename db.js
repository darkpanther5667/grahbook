const { MongoClient } = require('mongodb');
const fs = require('fs');
const path = require('path');

// MongoDB connection string must be supplied via environment variable to avoid committing secrets.
const MONGODB_URI = process.env.MONGODB_URI;
const DB_FILE = path.join(__dirname, 'db.json');

let client;
let db;
let useLocalFallback = false;

async function connectDB() {
  if (!MONGODB_URI) {
    useLocalFallback = true;
    console.warn('⚠️ MONGODB_URI not set. Using local db.json fallback.');
    return null;
  }
  
  if (!client) {
    client = new MongoClient(MONGODB_URI);
    await client.connect();
    db = client.db('sharma_store');
    console.log('✅ Connected to MongoDB Atlas');
  }
  return db;
}

// Helper to get backwards compatible full DB dump for dashboard.html and regex fallback
async function getFullDB() {
  if (useLocalFallback) {
    // Read from local JSON file
    try {
      const data = fs.readFileSync(DB_FILE, 'utf8');
      return JSON.parse(data);
    } catch (error) {
      console.error('Error reading local db.json:', error);
      return { shop: {}, customers: [], transactions: [], bills: [], staff: [], stores: [] };
    }
  }

  const database = await connectDB();
  const shop = await database.collection('shop').findOne({});
  const customers = await database.collection('customers').find({}).toArray();
  const transactions = await database.collection('transactions').find({}).toArray();
  const bills = await database.collection('bills').find({}).toArray();
  const staff = await database.collection('staff').find({}).toArray();
  const stores = await database.collection('stores').find({}).toArray();

  return { shop, customers, transactions, bills, staff, stores };
}

module.exports = {
  connectDB,
  getFullDB
};
