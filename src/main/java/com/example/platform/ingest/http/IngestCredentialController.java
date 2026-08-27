package com.example.platform.ingest.http;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.ingest.dto.IngestCredentialCreateDTO;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialUpdateDTO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关接入凭证管理接口层（ADMIN 专属管理面，用户 JWT 通道）
 *
 * <p>secret 仅创建与重置响应中一次性返回，任何查询接口不回显。</p>
 */
@Tag(name = "接入管理", description = "网关接入凭证管理（ADMIN 专属）")
@RestController
@RequestMapping("/ingest-credentials")
public class IngestCredentialController {

    private final IngestCredentialService ingestCredentialService;
    private final CurrentUserContext currentUserContext;

    public IngestCredentialController(IngestCredentialService ingestCredentialService,
                                      CurrentUserContext currentUserContext) {
        this.ingestCredentialService = ingestCredentialService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询凭证", description = "支持网关名/接入标识模糊与状态、仿真标记筛选；secret 不返回")
    @GetMapping("/page")
    public Result<Page<IngestCredentialVO>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10，上限100") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "网关名/接入标识关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选：0-启用 1-停用（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "仿真标记筛选：0-真实网关 1-仿真（可选）") @RequestParam(required = false) Integer simulated) {
        return Result.success(ingestCredentialService.pageCredentials(
                current, size, keyword, status, simulated));
    }

    @Operation(summary = "查询凭证详情", description = "凭证不存在返回 404；secret 不返回")
    @GetMapping("/{id}")
    public Result<IngestCredentialVO> getById(
            @Parameter(description = "凭证ID", required = true) @PathVariable Long id) {
        return Result.success(ingestCredentialService.getCredential(id));
    }

    @Operation(summary = "创建凭证", description = "ADMIN 专属；服务端生成 accessKey/secret，secret 仅本次返回")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "CREATE_INGEST_CREDENTIAL", detail = "网关名:#createDTO.gatewayName")
    public Result<IngestCredentialCreatedVO> create(
            @Valid @RequestBody IngestCredentialCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ingestCredentialService.createCredential(createDTO, user.userId()));
    }

    @Operation(summary = "更新凭证信息", description = "ADMIN 专属；仅允许改显示名与过期时间，改绑机房需重发凭证")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_INGEST_CREDENTIAL", detail = "凭证ID:#id")
    public Result<IngestCredentialVO> update(
            @Parameter(description = "凭证ID", required = true) @PathVariable Long id,
            @Valid @RequestBody IngestCredentialUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ingestCredentialService.updateCredential(id, updateDTO, user.userId()));
    }

    @Operation(summary = "启用/停用凭证", description = "ADMIN 专属；停用即切断该网关数据源")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_INGEST_CREDENTIAL_STATUS", detail = "凭证ID:#id, 状态:#status")
    public Result<IngestCredentialVO> updateStatus(
            @Parameter(description = "凭证ID", required = true) @PathVariable Long id,
            @Parameter(description = "目标状态：0-启用 1-停用", required = true) @RequestParam Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ingestCredentialService.updateStatus(id, status, user.userId()));
    }

    @Operation(summary = "重置签名密钥", description = "ADMIN 专属；旧 secret 立即失效，新 secret 仅本次返回")
    @PutMapping("/{id}/secret")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "RESET_INGEST_CREDENTIAL_SECRET", detail = "凭证ID:#id")
    public Result<IngestCredentialCreatedVO> resetSecret(
            @Parameter(description = "凭证ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ingestCredentialService.resetSecret(id, user.userId()));
    }

    @Operation(summary = "删除凭证", description = "ADMIN 专属；物理删除，网关侧持有的凭证立即失效")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_INGEST_CREDENTIAL", detail = "凭证ID:#id")
    public Result<Void> delete(
            @Parameter(description = "凭证ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        ingestCredentialService.deleteCredential(id, user.userId());
        return Result.success();
    }
}
