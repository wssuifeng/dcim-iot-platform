package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.entity.DictMetric;
import com.example.platform.service.DictMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metric")
@RequiredArgsConstructor
public class DictMetricController {

    private final DictMetricService dictMetricService;

    @GetMapping("/page")
    public Page<DictMetric> page(@RequestParam(defaultValue = "1") Integer current,
                                 @RequestParam(defaultValue = "10") Integer size) {
        return dictMetricService.page(new Page<>(current, size));
    }

    @PostMapping
    public boolean add(@RequestBody DictMetric metric) {
        return dictMetricService.save(metric);
    }

    @PutMapping
    public boolean update(@RequestBody DictMetric metric) {
        return dictMetricService.updateById(metric);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return dictMetricService.removeById(id);
    }

    /**
     * 手动刷新缓存接口（如果做了批量修改，可以调用此接口强制刷新）
     */
    @PostMapping("/refreshCache")
    public String refreshCache() {
        dictMetricService.refreshCache();
        return "缓存刷新成功";
    }
}
