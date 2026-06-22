let state = loadState();

function loadState() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return stored ? { ...structuredClone(seedState), ...stored } : structuredClone(seedState);
  } catch {
    return structuredClone(seedState);
  }
}

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function money(value) {
  return `¥${Number(value).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function price(value) {
  return Number(value).toFixed(2);
}

function nowText() {
  return new Date().toLocaleString("zh-CN", { hour12: false });
}

function currentAccount() {
  return state.currentAccount ? state.accounts[state.currentAccount] : null;
}

function getLimits(stock) {
  const explicitUpper = Number(stock.highLimit ?? stock.limitUp ?? stock.upperLimit);
  const explicitLower = Number(stock.lowLimit ?? stock.limitDown ?? stock.lowerLimit);
  if (Number.isFinite(explicitUpper) && Number.isFinite(explicitLower)) {
    return {
      upper: Number(explicitUpper.toFixed(2)),
      lower: Number(explicitLower.toFixed(2)),
    };
  }

  return {
    upper: Number((stock.prevClose * 1.1).toFixed(2)),
    lower: Number((stock.prevClose * 0.9).toFixed(2)),
  };
}

function setMessage(element, text, type = "") {
  element.textContent = text;
  element.className = `form-message ${type}`.trim();
}

function toast(message) {
  dom.toast.textContent = message;
  dom.toast.classList.remove("hidden");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => dom.toast.classList.add("hidden"), 3200);
}
