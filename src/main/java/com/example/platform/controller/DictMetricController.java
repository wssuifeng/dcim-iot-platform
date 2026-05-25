package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.entity.DictMetric;
import com.example.platform.service.DictMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
 * 类功能:指标字典接口层，提供指标字典的分页查询、增删改及缓存刷新等RESTful接口
 * */
@RestController
@RequestMapping("/metric")
@RequiredArgsConstructor
@Validated
public class DictMetricController {

    private final DictMetricService dictMetricService;

    /*
     * 方法功能:分页查询指标字典
     * 参数:current-当前页码, size-每页大小
     * 返回数据:Result<Page<DictMetric>>-分页指标字典数据
     * */
    @GetMapping("/page")
    public Result<Page<DictMetric>> page(@RequestParam(defaultValue = "1") Integer current,
                                         @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(dictMetricService.page(new Page<>(current, size)));
    }

    /*
     * 方法功能:新增指标字典条目
     * 参数:metric-指标字典对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PostMapping
    public Result<Void> add(@RequestBody DictMetric metric) {
        dictMetricService.save(metric);
        return Result.success();
    }

    /*
     * 方法功能:更新指标字典条目
     * 参数:metric-指标字典对象（需包含ID）
     * 返回数据:Result<Void>-操作结果
     * */
    @PutMapping
    public Result<Void> update(@RequestBody DictMetric metric) {
        dictMetricService.updateById(metric);
        return Result.success();
    }

    /*
     * 方法功能:根据ID删除指标字典条目
     * 参数:id-指标字典ID（路径参数）
     * 返回数据:Result<Void>-操作结果
     * */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        dictMetricService.removeById(id);
        return Result.success();
    }

    /*
     * 方法功能:手动刷新指标字典缓存
     * 参数:无
     * 返回数据:Result<Void>-操作结果
     * */
    @PostMapping("/cache/refresh")
    public Result<Void> refreshCache() {
        dictMetricService.refreshCache();
        return Result.success();
    }
}
