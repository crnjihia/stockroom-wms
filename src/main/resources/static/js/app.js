/**
 * Stockroom WMS - Core Application Logic
 * Nairobi FMCG Logistics & Warehouse Operations
 */

// Initial State with Authentic Nairobi FMCG Master Data
const state = {
  activeView: 'overview',
  activeWarehouseId: 1,
  
  warehouses: [
    { id: 1, code: 'NBO-01', name: 'Nairobi Central Depot', county: 'Nairobi', town: 'Industrial Area', address: 'Commercial Street, Gate 4', active: true, totalCapacity: 9300, occupied: 4120 },
    { id: 2, code: 'NBO-02', name: 'Embakasi Transit Hub', county: 'Nairobi', town: 'Embakasi', address: 'Airport North Road, Plot 12', active: true, totalCapacity: 7000, occupied: 2850 },
    { id: 3, code: 'NBO-03', name: 'Baba Dogo Distribution', county: 'Nairobi', town: 'Ruaraka', address: 'Baba Dogo Road, Shed B', active: true, totalCapacity: 5000, occupied: 1450 }
  ],
  
  locations: [
    { id: 1, warehouseId: 1, code: 'REC-01', zone: 'RECEIVING', capacity: 5000 },
    { id: 2, warehouseId: 1, code: 'A-01-01', zone: 'STORAGE', capacity: 1500 },
    { id: 3, warehouseId: 1, code: 'A-01-02', zone: 'STORAGE', capacity: 1500 },
    { id: 4, warehouseId: 1, code: 'PCK-01', zone: 'PICKING', capacity: 800 },
    { id: 5, warehouseId: 1, code: 'RET-01', zone: 'RETURNS', capacity: 500 },
    { id: 6, warehouseId: 2, code: 'REC-01', zone: 'RECEIVING', capacity: 4000 },
    { id: 7, warehouseId: 2, code: 'A-01-01', zone: 'STORAGE', capacity: 2000 },
    { id: 8, warehouseId: 2, code: 'PCK-01', zone: 'PICKING', capacity: 1000 }
  ],

  products: [
    { id: 1, sku: 'KEP-PEM-2KG', name: 'Pembe Maize Meal 2kg', category: 'Dry Goods', uom: 'BALE', barcode: '06161101234566', reorderPoint: 50, reorderQty: 200, unitCost: 185.00, unitPrice: 210.00, onHand: 200, reserved: 20, batch: 'BAT-PEM-2026-01', expiry: '2027-04-15' },
    { id: 2, sku: 'KEP-JOG-2KG', name: 'Jogoo Maize Meal 2kg', category: 'Dry Goods', uom: 'BALE', barcode: '06161102345674', reorderPoint: 40, reorderQty: 150, unitCost: 190.00, unitPrice: 215.00, onHand: 120, reserved: 0, batch: 'BAT-JOG-2026-01', expiry: '2027-03-20' },
    { id: 3, sku: 'KAP-RIN-5LT', name: 'Rina Pure Vegetable Oil 5L', category: 'Oils & Fats', uom: 'CARTON', barcode: '06161103456782', reorderPoint: 25, reorderQty: 100, unitCost: 1250.00, unitPrice: 1420.00, onHand: 80, reserved: 15, batch: 'BAT-RIN-2026-01', expiry: '2027-10-31' },
    { id: 4, sku: 'KET-TEA-100', name: 'Ketepa Chai Premium Tea Bags 100s', category: 'Beverages', uom: 'CARTON', barcode: '06161104567899', reorderPoint: 30, reorderQty: 80, unitCost: 275.00, unitPrice: 330.00, onHand: 60, reserved: 0, batch: 'BAT-KET-2026-01', expiry: '2028-09-01' },
    { id: 5, sku: 'BRK-MLK-500', name: 'Brookside Fresh Milk 500ml', category: 'Dairy', uom: 'CRATE', barcode: '06161105678906', reorderPoint: 60, reorderQty: 250, unitCost: 55.00, unitPrice: 68.00, onHand: 45, reserved: 0, batch: 'BAT-BRK-2026-01', expiry: '2026-10-28' },
    { id: 6, sku: 'UNI-OMO-1KG', name: 'Omo Hand Washing Powder 1kg', category: 'Cleaning', uom: 'CARTON', barcode: '06161106789013', reorderPoint: 20, reorderQty: 60, unitCost: 260.00, unitPrice: 310.00, onHand: 15, reserved: 0, batch: 'BAT-OMO-2026-01', expiry: '2028-05-15' }
  ],

  reservations: [
    { id: 1, orderNumber: 'ORD-NBO-8401', sku: 'KAP-RIN-5LT', quantity: 15, status: 'CONFIRMED', expiresAt: Date.now() + 6400000 },
    { id: 2, orderNumber: 'ORD-NBO-8402', sku: 'KEP-PEM-2KG', quantity: 20, status: 'CONFIRMED', expiresAt: Date.now() + 7100000 },
    { id: 3, orderNumber: 'ORD-NBO-8399', sku: 'BRK-MLK-500', quantity: 30, status: 'FULFILLED', expiresAt: Date.now() - 3600000 }
  ],

  backorders: [
    { id: 1, orderNumber: 'ORD-RET-901', sku: 'UNI-OMO-1KG', quantity: 25, status: 'PENDING', createdAt: '2026-10-16 14:20' }
  ],

  movements: [
    { id: 101, timestamp: '19:25', sku: 'KEP-PEM-2KG', location: 'NBO-01 / A-01-01', type: 'INBOUND', qty: '+150', ref: 'GRN-2026-001' },
    { id: 102, timestamp: '19:26', sku: 'KEP-PEM-2KG', location: 'NBO-01 / PCK-01', type: 'INBOUND', qty: '+50', ref: 'GRN-2026-002' },
    { id: 103, timestamp: '19:27', sku: 'KAP-RIN-5LT', location: 'NBO-01 / A-01-01', type: 'INBOUND', qty: '+80', ref: 'GRN-2026-004' },
    { id: 104, timestamp: '19:28', sku: 'KAP-RIN-5LT', location: 'NBO-01 / A-01-01', type: 'RESERVATION', qty: '-15', ref: 'ORD-NBO-8401' }
  ]
};

// Utilities
const formatKES = (amt) => 'KES ' + Number(amt).toLocaleString('en-KE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function showToast(msg, isSuccess = true) {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.style.borderLeftColor = isSuccess ? 'var(--accent-emerald)' : 'var(--accent-rose)';
  toast.innerHTML = `
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      ${isSuccess ? '<path d="M20 6L9 17l-5-5"/>' : '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>'}
    </svg>
    <span>${msg}</span>
  `;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// Navigation Handler
function navigateTo(viewId) {
  state.activeView = viewId;
  document.querySelectorAll('.nav-item').forEach(el => {
    if (el.dataset.view === viewId) {
      el.classList.add('active');
    } else {
      el.classList.remove('active');
    }
  });

  document.querySelectorAll('.view-section').forEach(sec => {
    if (sec.id === 'view-' + viewId) {
      sec.classList.add('active');
    } else {
      sec.classList.remove('active');
    }
  });

  renderActiveView();
}

function renderActiveView() {
  switch (state.activeView) {
    case 'overview':
      renderOverviewKPIs();
      renderInventoryTable('overviewInventoryTable', 4);
      break;
    case 'depots':
      renderDepots();
      break;
    case 'inventory':
      renderInventoryTable('fullInventoryTable');
      break;
    case 'reservations':
      renderReservations();
      break;
    case 'barcode':
      // barcode ready
      break;
    case 'reports':
      renderReports();
      break;
    case 'movements':
      renderMovements();
      break;
  }
}

// Render Overview
function renderOverviewKPIs() {
  const totalValuation = state.products.reduce((acc, p) => acc + (p.onHand * p.unitCost), 0);
  const totalUnits = state.products.reduce((acc, p) => acc + p.onHand, 0);
  const lowStockCount = state.products.filter(p => p.onHand <= p.reorderPoint).length;
  const activeReservationsCount = state.reservations.filter(r => r.status === 'CONFIRMED').length;

  const valEl = document.getElementById('kpiTotalValuation');
  if (valEl) valEl.innerText = formatKES(totalValuation);

  const unitsEl = document.getElementById('kpiTotalUnits');
  if (unitsEl) unitsEl.innerText = totalUnits.toLocaleString() + ' Units';

  const lowStockEl = document.getElementById('kpiLowStockCount');
  if (lowStockEl) lowStockEl.innerText = lowStockCount;

  const resEl = document.getElementById('kpiPendingReservations');
  if (resEl) resEl.innerText = activeReservationsCount;
}

// Render Inventory Table
function renderInventoryTable(tableId, limit = null) {
  const table = document.getElementById(tableId);
  if (!table) return;

  const tbody = table.querySelector('tbody');
  if (!tbody) return;

  const items = limit ? state.products.slice(0, limit) : state.products;

  tbody.innerHTML = items.map(p => {
    const available = p.onHand - p.reserved;
    const isLow = p.onHand <= p.reorderPoint;
    const isCritical = p.onHand <= (p.reorderPoint / 2);
    
    let statusBadge = '<span class="status-badge healthy">Optimal</span>';
    if (isCritical) {
      statusBadge = '<span class="status-badge danger">Reorder Critical</span>';
    } else if (isLow) {
      statusBadge = '<span class="status-badge warning">Low Stock</span>';
    }

    return `
      <tr>
        <td>
          <div class="sku-code">${p.sku}</div>
          <div class="barcode-sub">${p.barcode}</div>
        </td>
        <td>
          <div style="font-weight:600; color:var(--text-primary);">${p.name}</div>
          <div style="font-size:0.75rem; color:var(--text-muted);">${p.category} &bull; ${p.uom}</div>
        </td>
        <td>
          <div style="font-family:var(--font-mono); font-size:0.8rem;">${p.batch}</div>
          <div style="font-size:0.72rem; color:var(--text-muted);">Exp: ${p.expiry}</div>
        </td>
        <td><strong>${p.onHand}</strong></td>
        <td><span style="color:var(--accent-amber);">${p.reserved}</span></td>
        <td><strong style="color:var(--accent-emerald);">${available}</strong></td>
        <td>${formatKES(p.unitCost)}</td>
        <td>${statusBadge}</td>
        <td>
          <button class="btn btn-secondary btn-sm" onclick="openReceiveModal(${p.id})">Receive</button>
        </td>
      </tr>
    `;
  }).join('');
}

// Render Depots
function renderDepots() {
  const container = document.getElementById('depotsContainer');
  if (!container) return;

  container.innerHTML = state.warehouses.map(wh => {
    const pct = Math.round((wh.occupied / wh.totalCapacity) * 100);
    const whLocations = state.locations.filter(l => l.warehouseId === wh.id);

    return `
      <div class="depot-card">
        <div class="depot-header">
          <div>
            <div style="font-size:1.1rem; font-weight:700;">${wh.name}</div>
            <div style="font-size:0.8rem; color:var(--text-muted);">${wh.town}, ${wh.county} &bull; ${wh.address}</div>
          </div>
          <span class="depot-code">${wh.code}</span>
        </div>

        <div style="margin: 16px 0;">
          <div style="display:flex; justify-content:space-between; font-size:0.8rem; margin-bottom:6px;">
            <span style="color:var(--text-secondary);">Capacity Utilization</span>
            <strong>${wh.occupied.toLocaleString()} / ${wh.totalCapacity.toLocaleString()} (${pct}%)</strong>
          </div>
          <div style="width:100%; height:8px; background:var(--bg-secondary); border-radius:4px; overflow:hidden;">
            <div style="width:${pct}%; height:100%; background:linear-gradient(90deg, var(--accent-blue), var(--accent-emerald)); border-radius:4px;"></div>
          </div>
        </div>

        <div style="font-size:0.75rem; text-transform:uppercase; letter-spacing:0.05em; color:var(--text-muted); font-weight:600;">Configured Zones</div>
        <div class="zone-chips">
          ${whLocations.map(l => `<span class="zone-chip">${l.code} (${l.zone})</span>`).join('')}
        </div>
      </div>
    `;
  }).join('');
}

// Render Reservations
function renderReservations() {
  const tbody = document.querySelector('#reservationsTable tbody');
  if (!tbody) return;

  tbody.innerHTML = state.reservations.map(r => {
    const timeLeftMs = r.expiresAt - Date.now();
    let timerText = 'Expired';
    let badgeClass = 'status-badge danger';

    if (r.status === 'FULFILLED') {
      timerText = 'Dispatched';
      badgeClass = 'status-badge healthy';
    } else if (timeLeftMs > 0) {
      const mins = Math.floor(timeLeftMs / 60000);
      const secs = Math.floor((timeLeftMs % 60000) / 1000);
      timerText = `${mins}m ${secs}s remaining`;
      badgeClass = 'status-badge info';
    }

    return `
      <tr>
        <td style="font-family:var(--font-mono); font-weight:600; color:#60a5fa;">${r.orderNumber}</td>
        <td><strong>${r.sku}</strong></td>
        <td>${r.quantity} units</td>
        <td><span class="${badgeClass}">${r.status}</span></td>
        <td style="font-family:var(--font-mono); font-size:0.8rem;">${timerText}</td>
        <td>
          ${r.status === 'CONFIRMED' ? `
            <button class="btn btn-emerald btn-sm" onclick="fulfillReservation(${r.id})">Fulfill Order</button>
            <button class="btn btn-secondary btn-sm" onclick="cancelReservation(${r.id})">Cancel</button>
          ` : '<span style="color:var(--text-muted); font-size:0.8rem;">Completed</span>'}
        </td>
      </tr>
    `;
  }).join('');
}

function fulfillReservation(resId) {
  const r = state.reservations.find(x => x.id === resId);
  if (!r) return;
  const p = state.products.find(x => x.sku === r.sku);
  if (p) {
    p.onHand -= r.quantity;
    p.reserved -= r.quantity;
  }
  r.status = 'FULFILLED';
  state.movements.unshift({
    id: Date.now(),
    timestamp: new Date().toLocaleTimeString().slice(0, 5),
    sku: r.sku,
    location: 'NBO-01 / PCK-01',
    type: 'DISPATCH',
    qty: '-' + r.quantity,
    ref: r.orderNumber
  });
  showToast(`Order ${r.orderNumber} successfully fulfilled and dispatched!`);
  renderReservations();
  renderOverviewKPIs();
}

function cancelReservation(resId) {
  const r = state.reservations.find(x => x.id === resId);
  if (!r) return;
  const p = state.products.find(x => x.sku === r.sku);
  if (p) {
    p.reserved -= r.quantity;
  }
  state.reservations = state.reservations.filter(x => x.id !== resId);
  showToast(`Reservation for ${r.orderNumber} released.`);
  renderReservations();
  renderOverviewKPIs();
}

// Render Movements
function renderMovements() {
  const tbody = document.querySelector('#movementsTable tbody');
  if (!tbody) return;

  tbody.innerHTML = state.movements.map(m => {
    let color = 'var(--accent-emerald)';
    if (m.type === 'DISPATCH' || m.type === 'RESERVATION') color = 'var(--accent-rose)';

    return `
      <tr>
        <td style="font-family:var(--font-mono);">${m.timestamp}</td>
        <td class="sku-code">${m.sku}</td>
        <td>${m.location}</td>
        <td><span class="status-badge info">${m.type}</span></td>
        <td><strong style="color:${color}">${m.qty}</strong></td>
        <td style="font-family:var(--font-mono); font-size:0.8rem; color:var(--text-muted);">${m.ref}</td>
      </tr>
    `;
  }).join('');
}

// Render Reports Tab
function renderReports() {
  // reports static action links
}

// Barcode GS1-128 Scanner Simulator
function simulateScan(rawInput) {
  const laser = document.querySelector('.scanner-laser-line');
  if (laser) {
    laser.classList.add('scanning');
    setTimeout(() => laser.classList.remove('scanning'), 1200);
  }

  // Parse GS1-128
  const parsed = {
    gtin: null,
    batch: null,
    expiry: null,
    serial: null
  };

  // Support both (01)... bracketed format and plain AI format
  const aiRegex = /\((01|10|17|21)\)([^()]+)/g;
  let match;
  let hasBrackets = false;

  while ((match = aiRegex.exec(rawInput)) !== null) {
    hasBrackets = true;
    const ai = match[1];
    const val = match[2];
    if (ai === '01') parsed.gtin = val;
    if (ai === '10') parsed.batch = val;
    if (ai === '17') parsed.expiry = val;
    if (ai === '21') parsed.serial = val;
  }

  // Fallback for simple EAN-13 barcodes
  if (!hasBrackets) {
    parsed.gtin = rawInput.trim();
  }

  // Find product matching GTIN / barcode
  const matchedProduct = state.products.find(p => p.barcode === parsed.gtin || p.sku.toLowerCase() === rawInput.toLowerCase());

  const resultContainer = document.getElementById('scanResult');
  if (!resultContainer) return;

  if (matchedProduct) {
    resultContainer.innerHTML = `
      <div style="background:rgba(16, 185, 129, 0.08); border:1px solid rgba(16, 185, 129, 0.3); border-radius:var(--radius-md); padding:18px;">
        <div style="display:flex; justify-content:space-between; align-items:flex-start;">
          <div>
            <div style="font-size:1.15rem; font-weight:700; color:#34d399;">${matchedProduct.name}</div>
            <div class="sku-code">${matchedProduct.sku} &bull; ${matchedProduct.category}</div>
          </div>
          <span class="status-badge healthy">MATCH FOUND</span>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(130px, 1fr)); gap:12px; margin-top:14px; font-size:0.85rem;">
          <div><span style="color:var(--text-muted);">Current On-Hand:</span> <strong>${matchedProduct.onHand} units</strong></div>
          <div><span style="color:var(--text-muted);">Unit Cost:</span> <strong>${formatKES(matchedProduct.unitCost)}</strong></div>
          <div><span style="color:var(--text-muted);">Unit Price:</span> <strong>${formatKES(matchedProduct.unitPrice)}</strong></div>
          <div><span style="color:var(--text-muted);">Primary Location:</span> <strong>NBO-01 / A-01-01</strong></div>
        </div>

        <div class="ai-chips">
          ${parsed.gtin ? `<span class="ai-chip"><strong>AI (01) GTIN:</strong> ${parsed.gtin}</span>` : ''}
          ${parsed.batch ? `<span class="ai-chip"><strong>AI (10) BATCH:</strong> ${parsed.batch}</span>` : ''}
          ${parsed.expiry ? `<span class="ai-chip"><strong>AI (17) EXPIRY:</strong> ${parsed.expiry}</span>` : ''}
          ${parsed.serial ? `<span class="ai-chip"><strong>AI (21) SERIAL:</strong> ${parsed.serial}</span>` : ''}
        </div>
      </div>
    `;
    showToast(`Barcode Scanned: ${matchedProduct.sku}`);
  } else {
    resultContainer.innerHTML = `
      <div style="background:rgba(244, 63, 94, 0.08); border:1px solid rgba(244, 63, 94, 0.3); border-radius:var(--radius-md); padding:16px;">
        <div style="color:#fb7185; font-weight:600;">No product matched barcode "${rawInput}"</div>
        <div style="font-size:0.8rem; color:var(--text-muted); margin-top:4px;">Ensure the barcode is enrolled in the Nairobi FMCG product catalog.</div>
      </div>
    `;
  }
}

// Modal Inbound Receipt
function openReceiveModal(productId) {
  const p = state.products.find(x => x.id === productId);
  if (!p) return;

  const modal = document.getElementById('receiveModal');
  if (!modal) return;

  document.getElementById('receiveProductId').value = p.id;
  document.getElementById('receiveProductTitle').innerText = `${p.name} (${p.sku})`;
  document.getElementById('receiveQty').value = p.reorderQty || 50;
  document.getElementById('receiveBatch').value = 'BAT-' + new Date().getFullYear() + '-' + Math.floor(10 + Math.random() * 90);

  modal.classList.add('open');
}

function closeReceiveModal() {
  const modal = document.getElementById('receiveModal');
  if (modal) modal.classList.remove('open');
}

function submitReceiveStock() {
  const pId = Number(document.getElementById('receiveProductId').value);
  const qty = Number(document.getElementById('receiveQty').value);
  const batch = document.getElementById('receiveBatch').value.trim();
  const locationCode = document.getElementById('receiveLocation').value;

  const p = state.products.find(x => x.id === pId);
  if (!p || qty <= 0) {
    showToast('Please enter a valid quantity', false);
    return;
  }

  p.onHand += qty;
  p.batch = batch;

  state.movements.unshift({
    id: Date.now(),
    timestamp: new Date().toLocaleTimeString().slice(0, 5),
    sku: p.sku,
    location: 'NBO-01 / ' + locationCode,
    type: 'INBOUND',
    qty: '+' + qty,
    ref: 'GRN-' + Math.floor(1000 + Math.random() * 9000)
  });

  closeReceiveModal();
  showToast(`Successfully received ${qty} units of ${p.sku} into ${locationCode}!`);
  renderActiveView();
}

// Initialize System Clock & Polling
function startClock() {
  const clockEl = document.getElementById('nairobiClock');
  if (!clockEl) return;
  setInterval(() => {
    const now = new Date();
    clockEl.innerText = now.toLocaleTimeString('en-GB') + ' EAT (Nairobi)';
  }, 1000);
}

// Check Backend API Connection
async function checkApiConnection() {
  const statusEl = document.getElementById('apiStatusIndicator');
  if (!statusEl) return;
  try {
    const res = await fetch('/api/v1/actuator/health');
    if (res.ok) {
      statusEl.className = 'api-status';
      statusEl.innerHTML = '<span class="status-dot"></span><span>API Connected</span>';
    } else {
      throw new Error();
    }
  } catch (e) {
    statusEl.className = 'api-status offline';
    statusEl.innerHTML = '<span class="status-dot" style="background-color:var(--accent-amber)"></span><span>Offline Demo Mode</span>';
  }
}

// Document Ready
document.addEventListener('DOMContentLoaded', () => {
  startClock();
  checkApiConnection();
  navigateTo('overview');

  // Scanner Form Submit
  const scannerForm = document.getElementById('scannerForm');
  if (scannerForm) {
    scannerForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const input = document.getElementById('barcodeInput');
      if (input && input.value.trim()) {
        simulateScan(input.value.trim());
      }
    });
  }

  // Reservation Timer Heartbeat
  setInterval(() => {
    if (state.activeView === 'reservations') {
      renderReservations();
    }
  }, 1000);
});
