package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateSecurityAccountRequest {

    @NotBlank
    @Pattern(regexp = "^(个人|法人)$", message = "investor_type must be 个人 or 法人")
    @JsonProperty("investor_type")
    private String investorType;

    @NotBlank
    @JsonProperty("name")
    private String name;

    @JsonProperty("gender")
    private String gender;

    @NotBlank
    @JsonProperty("id_type")
    private String idType;

    @NotBlank
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
