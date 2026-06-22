async function reviewOrderByManagement(orderPayload) {
  if (!API_CONFIG.managementBaseUrl) return { ok: true, approved: true };
  const result = await requestJson(
    API_CONFIG.managementBaseUrl,
    API_CONFIG.endpoints.reviewOrder,
    {
      method: "POST",
      body: buildManagementReviewRequest(orderPayload),
    },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data.review || result.data;
  if (result.data.success === false || result.data.ok === false) {
    return {
      ok: false,
      approved: false,
      message: result.data.message || payload.reason || "交易管理系统审查失败",
    };
  }
  return {
    ok: true,
    approved: payload.approved !== false,
    reviewId: payload.reviewId || orderPayload.reviewId,
    riskLevel: payload.riskLevel || "LOW",
    rejectCode: payload.rejectCode || "",
    message:
      payload.reason ||
      payload.message ||
      result.data.message ||
      (payload.approved === false
        ? "交易管理系统审查未通过"
        : "交易管理系统审查通过"),
  };
}

function buildManagementReviewRequest(orderPayload) {
  return {
    reviewId: orderPayload.reviewId,
    orderId: orderPayload.orderId,
    accountId: orderPayload.fundAccountNo,
    fundAccountNo: orderPayload.fundAccountNo,
    securityAccountNo: orderPayload.securityAccountNo,
    userName: orderPayload.userName,
    stockCode: orderPayload.stockCode,
    side: orderPayload.direction,
    price: Number(orderPayload.price),
    quantity: Number(orderPayload.quantity),
    amount: Number(orderPayload.price) * Number(orderPayload.quantity),
    clientTime: orderPayload.clientTime || new Date().toISOString(),
  };
}
