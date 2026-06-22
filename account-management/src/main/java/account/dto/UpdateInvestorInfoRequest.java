package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdateInvestorInfoRequest {

    @NotNull
    @Positive
    @JsonProperty("investor_id")
    private Integer investorId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("id_type")
    private String idType;

    @JsonProperty("id_number")
    private String idNumber;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("address")
    private String address;

    @JsonProperty("work_unit")
    private String workUnit;

    @JsonProperty("occupation")
    private String occupation;

    @JsonProperty("education")
    private String education;

    @JsonProperty("legal_number")
    private String legalNumber;

    @JsonProperty("business_license")
    private String businessLicense;

    @JsonProperty("executor_name")
    private String executorName;

    @JsonProperty("executor_id_number")
    private String executorIdNumber;

    @JsonProperty("executor_phone")
    private String executorPhone;

    @JsonProperty("executor_address")
    private String executorAddress;

    @JsonProperty("agent_name")
    private String agentName;

    @JsonProperty("agent_id_number")
    private String agentIdNumber;

    @JsonProperty("staff_id")
    private Integer staffId;
}
