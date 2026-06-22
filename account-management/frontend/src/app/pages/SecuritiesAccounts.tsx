import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { AlertCircle, Pencil, Plus, RefreshCw, Search, XCircle } from "lucide-react";
import { Button } from "../components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { api } from "../lib/api";

interface SecurityAccount {
  sec_acc_no: string;
  investor_id: number;
  name: string;
  id_number: string;
  investor_type: string;
  status: string;
  open_date: string;
  linked_fund_acc?: string;
}

type ActionType = "loss" | "reissue" | "close" | "edit";
type InvestorType = "个人" | "法人";
type AttendanceType = "self" | "agent";
type SecurityQuickAction = "create" | "loss" | "reissue" | "close";

function maskIdNumber(idNumber: string) {
  if (!idNumber) return "";
  if (idNumber.length <= 8) return idNumber;
  return `${idNumber.slice(0, 3)}********${idNumber.slice(-4)}`;
}

export default function SecuritiesAccounts() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [accounts, setAccounts] = useState<SecurityAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [isAccountModalOpen, setIsAccountModalOpen] = useState(false);
  const [investorType, setInvestorType] = useState<InvestorType>("个人");
  const [attendanceType, setAttendanceType] = useState<AttendanceType>("self");
  const [isSubmittingCreate, setIsSubmittingCreate] = useState(false);
  const [createError, setCreateError] = useState("");
  const [reissueOldSecAccNo, setReissueOldSecAccNo] = useState<string | null>(null);

  const [selectedAccount, setSelectedAccount] = useState<SecurityAccount | null>(null);
  const [actionType, setActionType] = useState<ActionType | null>(null);
  const [actionModalOpen, setActionModalOpen] = useState(false);
  const [actionIdNumber, setActionIdNumber] = useState("");
  const [actionReason, setActionReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");
  const [quickAction, setQuickAction] = useState<SecurityQuickAction | null>(null);
  const [quickActionAccountNo, setQuickActionAccountNo] = useState("");
  const [quickActionError, setQuickActionError] = useState("");
  const [quickActionModalOpen, setQuickActionModalOpen] = useState(false);

  const [editForm, setEditForm] = useState<Record<string, string>>({});

  useEffect(() => {
    void fetchAccounts();
  }, []);

  useEffect(() => {
    if (loading) {
      return;
    }

    const action = searchParams.get("action") as SecurityQuickAction | null;
    if (!action) {
      return;
    }

    if (action === "create") {
      setCreateError("");
      setReissueOldSecAccNo(null);
      setInvestorType("个人");
      setAttendanceType("self");
      setIsAccountModalOpen(true);
      setSearchParams({}, { replace: true });
      return;
    }

    setQuickAction(action);
    setQuickActionAccountNo("");
    setQuickActionError("");
    setQuickActionModalOpen(true);
    setSearchParams({}, { replace: true });
  }, [loading, searchParams, setSearchParams]);

  const fetchAccounts = async () => {
    try {
      setLoading(true);
      const response = await api.listSecurityAccounts();
      setAccounts(Array.isArray(response) ? response : []);
    } catch (error) {
      console.error("获取证券账户列表失败:", error);
    } finally {
      setLoading(false);
    }
  };

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.status !== "closed"),
    [accounts]
  );

  const filteredAccounts = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    if (!keyword) return activeAccounts;
    return activeAccounts.filter((account) => {
      const searchFields = [
        account.name,
        account.sec_acc_no,
        account.investor_type,
        account.linked_fund_acc || "",
      ];
      return searchFields.some((value) => value?.toLowerCase().includes(keyword));
    });
  }, [activeAccounts, searchTerm]);

  const normalAccounts = useMemo(
    () => filteredAccounts.filter((account) => account.status === "normal"),
    [filteredAccounts]
  );

  const frozenAccounts = useMemo(
    () => filteredAccounts.filter((account) => account.status !== "normal"),
    [filteredAccounts]
  );

  const resetCreateForm = () => {
    setReissueOldSecAccNo(null);
    setInvestorType("个人");
    setAttendanceType("self");
    setCreateError("");
    const form = document.getElementById("security-account-form") as HTMLFormElement | null;
    form?.reset();
  };

  const handleCreateAccount = async () => {
    const form = document.getElementById("security-account-form") as HTMLFormElement | null;
    if (!form) return;

    const formData = new FormData(form);
    const basePayload = {
      investor_type: investorType,
      name: String(formData.get("name") || "").trim(),
      id_type: String(formData.get("id_type") || "").trim(),
      id_number: String(formData.get("id_number") || "").trim(),
      phone: String(formData.get("phone") || "").trim(),
      address: String(formData.get("address") || "").trim(),
      work_unit: String(formData.get("work_unit") || "").trim(),
      occupation: String(formData.get("occupation") || "").trim(),
      education: String(formData.get("education") || "").trim(),
    };

    let payload: Record<string, string> = {
      ...basePayload,
      gender: String(formData.get("gender") || "").trim(),
    };

    if (!payload.name || !payload.id_type || !payload.id_number) {
      setCreateError("请填写完整的开户信息");
      return;
    }

    if (investorType === "法人") {
      payload = {
        ...basePayload,
        legal_number: String(formData.get("legal_number") || "").trim(),
        business_license: String(formData.get("business_license") || "").trim(),
        executor_name: String(formData.get("executor_name") || "").trim(),
        executor_id_number: String(formData.get("executor_id_number") || "").trim(),
        executor_phone: String(formData.get("executor_phone") || "").trim(),
        executor_address: String(formData.get("executor_address") || "").trim(),
      };
    } else {
      payload = {
        ...payload,
        agent_name: attendanceType === "agent" ? String(formData.get("agent_name") || "").trim() : "",
        agent_id_number:
          attendanceType === "agent" ? String(formData.get("agent_id_number") || "").trim() : "",
      };
    }

    if (investorType === "个人" && attendanceType === "agent") {
      if (!payload.agent_name || !payload.agent_id_number) {
        setCreateError("个人代办开户时，请填写代办人姓名和证件号码");
        return;
      }
    }

    if (investorType === "法人") {
      const requiredCorpFields = [
        "legal_number",
        "business_license",
        "executor_name",
        "executor_id_number",
        "executor_phone",
        "executor_address",
      ] as const;
      const missingField = requiredCorpFields.find((field) => !payload[field]);
      if (missingField) {
        setCreateError("请填写完整的法人开户信息");
        return;
      }
    }

    setIsSubmittingCreate(true);
    setCreateError("");
    try {
      const result = reissueOldSecAccNo
        ? await api.reissueSecurityAccount(reissueOldSecAccNo, payload)
        : await api.createSecuritiesAccount(payload);
      alert(
        reissueOldSecAccNo
          ? `补办成功，新证券账户号：${result.new_sec_acc_no || "已生成"}`
          : `开户成功，证券账户号：${result.sec_acc_no || "已生成"}`
      );
      setIsAccountModalOpen(false);
      resetCreateForm();
      await fetchAccounts();
    } catch (error: any) {
      setCreateError(error.message || "开户失败");
    } finally {
      setIsSubmittingCreate(false);
    }
  };

  const openActionModal = (account: SecurityAccount, type: ActionType) => {
    if (type === "reissue") {
      setReissueOldSecAccNo(account.sec_acc_no);
      setInvestorType(account.investor_type === "法人" ? "法人" : "个人");
      setAttendanceType("self");
      setCreateError("");
      setIsAccountModalOpen(true);
      return;
    }
    setSelectedAccount(account);
    setActionType(type);
    setActionIdNumber("");
    setActionReason("");
    setActionError("");
    setActionModalOpen(true);

    if (type === "edit") {
      setEditForm({
        name: account.name,
        id_type: account.investor_type === "法人" ? "营业执照" : "身份证",
        id_number: account.id_number,
        gender: "",
        phone: "",
        address: "",
        work_unit: "",
        occupation: "",
        education: "",
      });
    }
  };

  const handleQuickActionSubmit = () => {
    if (!quickAction) return;

    const accountNo = quickActionAccountNo.trim();
    if (!accountNo) {
      setQuickActionError("请输入证券账户号");
      return;
    }

    const account = accounts.find((item) => item.sec_acc_no === accountNo);
    if (!account) {
      setQuickActionError("未找到对应的证券账户");
      return;
    }

    if (quickAction === "loss" || quickAction === "close") {
      if (account.status !== "normal") {
        setQuickActionError("该证券账户当前不是正常状态，无法执行该操作");
        return;
      }
      setQuickActionModalOpen(false);
      openActionModal(account, quickAction);
      return;
    }

    if (quickAction === "reissue") {
      if (account.status === "normal") {
        setQuickActionError("只有冻结状态的证券账户才能补办");
        return;
      }
      setQuickActionModalOpen(false);
      openActionModal(account, "reissue");
    }
  };

  const getQuickActionTitle = () => {
    switch (quickAction) {
      case "loss":
        return "证券账户挂失";
      case "reissue":
        return "证券账户补办";
      case "close":
        return "证券账户销户";
      default:
        return "证券账户操作";
    }
  };

  const handleActionSubmit = async () => {
    if (!selectedAccount || !actionType) return;

    if (actionType !== "edit" && !actionIdNumber.trim()) {
      setActionError("请由工作人员手动输入身份证号进行校验");
      return;
    }

    setActionLoading(true);
    setActionError("");
    try {
      if (actionType === "loss") {
        await api.reportSecurityLoss(selectedAccount.sec_acc_no, actionReason, actionIdNumber.trim());
      } else if (actionType === "close") {
        await api.closeSecurityAccount(selectedAccount.sec_acc_no, actionReason, actionIdNumber.trim());
      } else if (actionType === "edit") {
        await api.updateInvestorInfo(selectedAccount.investor_id, editForm);
      }

      setActionModalOpen(false);
      await fetchAccounts();
    } catch (error: any) {
      setActionError(error.message || "操作失败");
    } finally {
      setActionLoading(false);
    }
  };

  const renderRows = (list: SecurityAccount[], emptyText: string) => {
    if (loading) {
      return (
        <TableRow>
          <TableCell colSpan={8} className="py-8 text-center text-slate-500">
            加载中...
          </TableCell>
        </TableRow>
      );
    }

    if (list.length === 0) {
      return (
        <TableRow>
          <TableCell colSpan={8} className="py-8 text-center text-slate-500">
            {emptyText}
          </TableCell>
        </TableRow>
      );
    }

    return list.map((account) => (
      <TableRow key={account.sec_acc_no} data-testid={`security-row-${account.sec_acc_no}`}>
        <TableCell className="font-medium">{account.sec_acc_no}</TableCell>
        <TableCell>{account.investor_type}</TableCell>
        <TableCell>{account.name}</TableCell>
        <TableCell className="text-slate-500">{maskIdNumber(account.id_number)}</TableCell>
        <TableCell className="text-slate-500">{account.linked_fund_acc || "未绑定"}</TableCell>
        <TableCell>
          {account.status === "normal" ? (
            <span className="inline-flex items-center rounded-md bg-green-50 px-2 py-1 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
              正常
            </span>
          ) : (
            <span className="inline-flex items-center rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-800 ring-1 ring-inset ring-yellow-600/20">
              冻结
            </span>
          )}
        </TableCell>
        <TableCell className="text-slate-500">{account.open_date}</TableCell>
        <TableCell className="text-right">
          <div className="flex justify-end gap-2">
            <Button
              data-testid={`security-edit-${account.sec_acc_no}`}
              variant="outline"
              size="sm"
              className="h-8 border-slate-200 text-slate-600 hover:bg-slate-50"
              onClick={() => openActionModal(account, "edit")}
            >
              <Pencil className="mr-1 h-3 w-3" />
              修改信息
            </Button>
            {account.status === "normal" && (
              <>
                <Button
                  data-testid={`security-loss-${account.sec_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-yellow-200 text-yellow-700 hover:bg-yellow-50"
                  onClick={() => openActionModal(account, "loss")}
                >
                  <AlertCircle className="mr-1 h-3 w-3" />
                  挂失
                </Button>
                <Button
                  data-testid={`security-close-${account.sec_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-red-200 text-red-600 hover:bg-red-50"
                  onClick={() => openActionModal(account, "close")}
                >
                  <XCircle className="mr-1 h-3 w-3" />
                  销户
                </Button>
              </>
            )}
            {account.status !== "normal" && (
              <Button
                data-testid={`security-reissue-${account.sec_acc_no}`}
                variant="outline"
                size="sm"
                className="h-8 border-red-200 text-red-600 hover:bg-red-50"
                onClick={() => openActionModal(account, "reissue")}
              >
                <RefreshCw className="mr-1 h-3 w-3" />
                补办
              </Button>
            )}
          </div>
        </TableCell>
      </TableRow>
    ));
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">证券账户业务</h2>
          <p className="text-slate-500">管理投资者证券账户的开户、挂失、补办、销户与信息维护</p>
        </div>
        <Dialog
          open={isAccountModalOpen}
          onOpenChange={(open) => {
            setIsAccountModalOpen(open);
            if (!open) {
              resetCreateForm();
            }
          }}
        >
          <DialogTrigger asChild>
            <Button data-testid="open-security-create" className="bg-red-600 hover:bg-red-700">
              <Plus className="mr-2 h-4 w-4" />
              开设证券账户
            </Button>
          </DialogTrigger>
          <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
            <DialogHeader>
              <DialogTitle>{reissueOldSecAccNo ? "补办证券账户" : "开设证券账户"}</DialogTitle>
              <DialogDescription>
                {reissueOldSecAccNo
                  ? `按正常开户手续重新填写全部资料，原账户：${reissueOldSecAccNo}`
                  : "填写完整开户信息，由后端统一完成资格校验与开户处理。"}
              </DialogDescription>
            </DialogHeader>
            <Tabs
              value={investorType}
              onValueChange={(value) => setInvestorType(value as InvestorType)}
              className="mt-4"
            >
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger data-testid="security-tab-personal" value="个人">个人账户</TabsTrigger>
                <TabsTrigger data-testid="security-tab-corporate" value="法人">法人账户</TabsTrigger>
              </TabsList>
              <form id="security-account-form" className="space-y-4 py-4">
                <TabsContent value="个人" className="space-y-4">
                  <div className="space-y-2">
                    <Label>开户方式</Label>
                    <div className="flex gap-2">
                      <Button
                        data-testid="security-attendance-self"
                        type="button"
                        variant={attendanceType === "self" ? "default" : "outline"}
                        className={attendanceType === "self" ? "bg-red-600 hover:bg-red-700" : ""}
                        onClick={() => setAttendanceType("self")}
                      >
                        本人办理
                      </Button>
                      <Button
                        data-testid="security-attendance-agent"
                        type="button"
                        variant={attendanceType === "agent" ? "default" : "outline"}
                        className={attendanceType === "agent" ? "bg-red-600 hover:bg-red-700" : ""}
                        onClick={() => setAttendanceType("agent")}
                      >
                        代办开户
                      </Button>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="person-name">姓名</Label>
                      <Input id="person-name" name="name" placeholder="请输入真实姓名" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-gender">性别</Label>
                      <select
                        id="person-gender"
                        name="gender"
                        className="flex h-10 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                      >
                        <option value="男">男</option>
                        <option value="女">女</option>
                      </select>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-id-type">证件类型</Label>
                      <Input id="person-id-type" name="id_type" defaultValue="身份证" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-id-number">证件号码</Label>
                      <Input id="person-id-number" name="id_number" placeholder="请输入 18 位身份证号" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-phone">联系电话</Label>
                      <Input id="person-phone" name="phone" placeholder="请输入联系电话" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-work-unit">工作单位</Label>
                      <Input id="person-work-unit" name="work_unit" placeholder="请输入工作单位" />
                    </div>
                    <div className="col-span-2 space-y-2">
                      <Label htmlFor="person-address">家庭地址</Label>
                      <Input id="person-address" name="address" placeholder="请输入家庭地址" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-occupation">职业</Label>
                      <Input id="person-occupation" name="occupation" placeholder="请输入职业" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="person-education">学历</Label>
                      <Input id="person-education" name="education" placeholder="请输入学历" />
                    </div>
                    {attendanceType === "agent" && (
                      <>
                        <div className="space-y-2">
                          <Label htmlFor="person-agent-name">代办人姓名</Label>
                          <Input id="person-agent-name" name="agent_name" placeholder="请输入代办人姓名" />
                        </div>
                        <div className="space-y-2">
                          <Label htmlFor="person-agent-id-number">代办人证件号码</Label>
                          <Input
                            id="person-agent-id-number"
                            name="agent_id_number"
                            placeholder="请输入代办人证件号码"
                          />
                        </div>
                      </>
                    )}
                  </div>
                </TabsContent>

                <TabsContent value="法人" className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="corp-name">法人姓名</Label>
                      <Input id="corp-name" name="name" placeholder="请输入法人姓名" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-id-type">证件类型</Label>
                      <Input id="corp-id-type" name="id_type" defaultValue="法定代表人身份证" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-id-number">法定代表人身份证号码</Label>
                      <Input
                        id="corp-id-number"
                        name="id_number"
                        placeholder="请输入法定代表人身份证号码"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-legal-number">有效的法人注册登记号码</Label>
                      <Input
                        id="corp-legal-number"
                        name="legal_number"
                        placeholder="请输入法人注册登记号码"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-business-license">营业执照号码</Label>
                      <Input
                        id="corp-business-license"
                        name="business_license"
                        placeholder="请输入营业执照号码"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-phone">联系电话</Label>
                      <Input id="corp-phone" name="phone" placeholder="请输入联系电话" />
                    </div>
                    <div className="col-span-2 space-y-2">
                      <Label htmlFor="corp-address">联系地址</Label>
                      <Input id="corp-address" name="address" placeholder="请输入联系地址" />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-executor-id-number">授权执行人有效身份证号码</Label>
                      <Input
                        id="corp-executor-id-number"
                        name="executor_id_number"
                        placeholder="请输入授权执行人有效身份证号码"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-executor-phone">授权执行人联系电话</Label>
                      <Input id="corp-executor-phone" name="executor_phone" placeholder="请输入授权执行人联系电话" />
                    </div>
                    <div className="col-span-2 space-y-2">
                      <Label htmlFor="corp-executor-address">授权执行人地址</Label>
                      <Input
                        id="corp-executor-address"
                        name="executor_address"
                        placeholder="请输入授权执行人地址"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="corp-executor-name">法定代表人授权证券交易执行人的姓名</Label>
                      <Input
                        id="corp-executor-name"
                        name="executor_name"
                        placeholder="请输入授权证券交易执行人姓名"
                      />
                    </div>
                  </div>
                </TabsContent>
              </form>
            </Tabs>
            {createError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
                {createError}
              </div>
            )}
            <DialogFooter>
              <Button variant="outline" onClick={() => setIsAccountModalOpen(false)}>
                取消
              </Button>
            <Button data-testid="security-create-submit" onClick={handleCreateAccount} disabled={isSubmittingCreate}>
                {isSubmittingCreate ? "提交中..." : reissueOldSecAccNo ? "确认补办" : "确认开户"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <div className="flex max-w-sm items-center gap-2">
        <div className="relative w-full">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-500" />
          <Input
            className="pl-9"
            placeholder="搜索姓名 / 账户号 / 类型 / 关联资金账户..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <Button variant="outline" size="sm" onClick={fetchAccounts} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      <div className="space-y-4">
        <div>
          <h3 className="mb-2 text-lg font-semibold">正常账户</h3>
          <div className="rounded-md border border-slate-200 bg-white">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>证券账户号</TableHead>
                  <TableHead>账户类型</TableHead>
                  <TableHead>姓名 / 法人</TableHead>
                  <TableHead>证件号</TableHead>
                  <TableHead>关联资金账户</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>登记日期</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>{renderRows(normalAccounts, "暂无正常证券账户")}</TableBody>
            </Table>
          </div>
        </div>

        <div>
          <h3 className="mb-2 text-lg font-semibold">冻结账户</h3>
          <div className="rounded-md border border-slate-200 bg-white">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>证券账户号</TableHead>
                  <TableHead>账户类型</TableHead>
                  <TableHead>姓名 / 法人</TableHead>
                  <TableHead>证件号</TableHead>
                  <TableHead>关联资金账户</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>登记日期</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>{renderRows(frozenAccounts, "暂无冻结证券账户")}</TableBody>
            </Table>
          </div>
        </div>
      </div>

      <Dialog open={actionModalOpen} onOpenChange={setActionModalOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>
              {actionType === "loss"
                ? "证券账户挂失"
                : actionType === "reissue"
                ? "证券账户补办"
                : actionType === "close"
                ? "证券账户销户"
                : "修改投资者信息"}
            </DialogTitle>
            <DialogDescription>
              {actionType === "edit"
                ? "修改前请核对投资者信息。"
                : "请由工作人员手动输入身份证号进行身份校验。"}
            </DialogDescription>
          </DialogHeader>

          {actionType === "edit" ? (
            <div className="grid grid-cols-2 gap-4 py-4">
              <div className="space-y-2">
                <Label>姓名</Label>
                <Input
                  data-testid="security-edit-name"
                  value={editForm.name || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label>证件类型</Label>
                <Input
                  data-testid="security-edit-id-type"
                  value={editForm.id_type || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, id_type: e.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label>证件号码</Label>
                <Input
                  data-testid="security-edit-id-number"
                  value={editForm.id_number || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, id_number: e.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label>联系电话</Label>
                <Input
                  data-testid="security-edit-phone"
                  value={editForm.phone || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, phone: e.target.value }))}
                />
              </div>
              <div className="col-span-2 space-y-2">
                <Label>地址</Label>
                <Input
                  data-testid="security-edit-address"
                  value={editForm.address || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, address: e.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label>工作单位</Label>
                <Input
                  data-testid="security-edit-work-unit"
                  value={editForm.work_unit || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, work_unit: e.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label>职业</Label>
                <Input
                  data-testid="security-edit-occupation"
                  value={editForm.occupation || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, occupation: e.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label>学历</Label>
                <Input
                  data-testid="security-edit-education"
                  value={editForm.education || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, education: e.target.value }))}
                />
              </div>
            </div>
          ) : (
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label>身份证号 / 证件号</Label>
                <Input
                  data-testid="security-action-id-number"
                  value={actionIdNumber}
                  onChange={(e) => setActionIdNumber(e.target.value)}
                  placeholder="请由工作人员手动输入证件号"
                />
              </div>
              {(actionType === "loss" || actionType === "close") && (
                <div className="space-y-2">
                  <Label>操作原因</Label>
                  <Input
                    data-testid="security-action-reason"
                    value={actionReason}
                    onChange={(e) => setActionReason(e.target.value)}
                    placeholder="请输入操作原因"
                  />
                </div>
              )}
            </div>
          )}

          {actionError && (
            <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">{actionError}</div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setActionModalOpen(false)}>
              取消
            </Button>
            <Button data-testid="security-action-submit" onClick={handleActionSubmit} disabled={actionLoading}>
              {actionLoading ? "处理中..." : "确认提交"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={quickActionModalOpen}
        onOpenChange={(open) => {
          setQuickActionModalOpen(open);
          if (!open) {
            setQuickAction(null);
            setQuickActionAccountNo("");
            setQuickActionError("");
          }
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{getQuickActionTitle()}</DialogTitle>
            <DialogDescription>请先输入证券账户号，再进入对应的校验和操作流程。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="security-quick-action-account">证券账户号</Label>
              <Input
                id="security-quick-action-account"
                value={quickActionAccountNo}
                onChange={(e) => setQuickActionAccountNo(e.target.value)}
                placeholder="请输入 SA..."
              />
            </div>
            {quickActionError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
                {quickActionError}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setQuickActionModalOpen(false)}>
              取消
            </Button>
            <Button onClick={handleQuickActionSubmit}>继续</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
