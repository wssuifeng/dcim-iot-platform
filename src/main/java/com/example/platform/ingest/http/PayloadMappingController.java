package com.example.platform.ingest.http;

import com.example.platform.common.Result;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.ingest.dto.PayloadDryRunVO;
import com.example.platform.ingest.dto.PayloadMappingDTO;
import com.example.platform.ingest.entity.IngestPayloadMapping;
import com.example.platform.ingest.entity.IngestRawMessage;
import com.example.platform.ingest.service.IngestPayloadMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/*
 * 类功能:自定义报文映射模板控制器（零散设备接入，ADR-009）
 *   ADMIN 专属：模板 CRUD + dry-run 预览 + 机房原始报文留存查询
 * */
@RestController
@RequestMapping("/payload-mappings")
@RequiredArgsConstructor
@Tag(name = "自定义报文映射", description = "零散设备报文方言适配（特征匹配路由 + 字段路径转译，ADR-009）")
public class PayloadMappingController {

    private final IngestPayloadMappingService mappingService;
    private final CurrentUserContext currentUserContext;

    @Operation(summary = "按机房查询映射模板列表")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<PayloadMappingDTO>> list(
            @Parameter(description = "机房ID", required = true) @RequestParam Long roomId) {
        return Result.success(mappingService.listByRoom(roomId));
    }

    @Operation(summary = "新建映射模板", description = "设备编码来源二选一（报文路径/固定值）；"
            + "字段映射的指标编码必须已在指标字典登记")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PayloadMappingDTO> create(@Valid @RequestBody PayloadMappingDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(mappingService.create(dto, user.userId()));
    }

    @Operation(summary = "更新映射模板")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PayloadMappingDTO> update(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PayloadMappingDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(mappingService.update(id, dto, user.userId()));
    }

    @Operation(summary = "删除映射模板（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        mappingService.delete(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "dry-run 转译预览", description = "用模板草稿（可不落库）对原始报文做匹配+转译，"
            + "返回命中模板/设备编码/采样项/说明；纯预览不落采样")
    @PostMapping("/dry-run")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PayloadDryRunVO> dryRun(@RequestBody PayloadDryRunRequest request) {
        return Result.success(mappingService.dryRun(request.getMapping(), request.getRawMessage()));
    }

    @Operation(summary = "查询机房最新原始报文", description = "开放通道未匹配报文的留存（每凭证最新一条），"
            + "作为映射配置参照物")
    @GetMapping("/raw-message")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<IngestRawMessage> latestRawMessage(
            @Parameter(description = "机房ID", required = true) @RequestParam Long roomId) {
        return Result.success(mappingService.latestRawMessage(roomId));
    }

    /**
     * dry-run 请求体：模板草稿 + 原始报文
     */
    @lombok.Data
    public static class PayloadDryRunRequest {
        @Parameter(description = "模板草稿（可不落库）")
        private PayloadMappingDTO mapping;
        @Parameter(description = "原始报文 JSON 文本", required = true)
        private String rawMessage;
    }
}
