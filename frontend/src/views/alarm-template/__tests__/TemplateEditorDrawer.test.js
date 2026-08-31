import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 每例卸载组件：清理 EP teleport 到 body 的下拉残留，避免跨测试点选到旧实例
enableAutoUnmount(afterEach)

vi.mock('@/api/alarmTemplate', () => ({
  getTemplate: vi.fn().mockResolvedValue({ data: { templateName: '温湿度标准' } }),
  listTemplateItems: vi.fn().mockResolvedValue({ data: [] }),
  createTemplateItem: vi.fn().mockResolvedValue({ data: {} }),
  updateTemplateItem: vi.fn().mockResolvedValue({ data: {} }),
  deleteTemplateItem: vi.fn().mockResolvedValue({ data: {} }),
}))

import TemplateEditorDrawer from '../TemplateEditorDrawer.vue'
import {
  createTemplateItem,
  getTemplate,
  listTemplateItems,
  updateTemplateItem,
} from '@/api/alarmTemplate'

/*
 * 测试目标：告警模版规则行编辑保存流（F7 回归面）+ S8-1 机制字段整组语义
 * ——保存 payload 带机制组、折叠机制视为禁用、编辑回填、启用开关不携带机制组（部分更新安全）、表单校验
 */

function mountDrawer(items = []) {
  listTemplateItems.mockResolvedValue({ data: items })
  return mount(TemplateEditorDrawer, {
    props: {
      visible: true,
      templateId: 1,
      canWrite: true,
      points: [{ id: 5, pointCode: 'temperature', pointName: '温度' }],
    },
    global: {
      plugins: [ElementPlus],
      stubs: {
        // 模拟真实 el-drawer：挂载即触发 open → 组件 fetchAll
        'el-drawer': {
          template: '<div><slot /></div>',
          emits: ['open'],
          mounted() { this.$emit('open') },
        },
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
      },
    },
  })
}

// 表单内按 label 定位 form-item 的输入
async function fillInput(wrapper, labelText, value) {
  const formItem = wrapper.findAll('.el-form-item')
    .find(node => node.find('.el-form-item__label').text() === labelText)
  const input = formItem.find('input')
  await input.setValue(value)
  await input.trigger('blur')
  await flushPromises()
}

// el-select 下拉渲染在 body 上，按 label 定位选择器后点选 body 内选项
async function selectOption(wrapper, labelText, optionText) {
  const formItem = wrapper.findAll('.el-form-item')
    .find(node => node.find('.el-form-item__label').text() === labelText)
  await formItem.find('.el-select').trigger('click')
  await flushPromises()
  const option = [...document.querySelectorAll('.el-select-dropdown__item')]
    .find(node => node.textContent.includes(optionText))
  if (!option) {
    throw new Error(`下拉选项未找到: ${optionText}`)
  }
  option.click()
  await flushPromises()
  await flushPromises()
}

async function clickButton(wrapper, text) {
  const button = wrapper.findAll('button').find(node => node.text().includes(text))
  await button.trigger('click')
  await flushPromises()
}

// el-input-number 带 precision 时显示补零，按数值比较
function numericInputValue(formItem) {
  return Number(formItem.find('input').element.value)
}

describe('TemplateEditorDrawer 规则行编辑保存流', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('打开抽屉时拉取模版与规则行', async () => {
    mountDrawer()
    await flushPromises()
    expect(getTemplate).toHaveBeenCalledWith(1)
    expect(listTemplateItems).toHaveBeenCalledWith(1)
  })

  it('新增 METRIC 规则：payload 携带机制组（默认全关）与基础字段', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await clickButton(wrapper, '新增规则行')

    await selectOption(wrapper, '指标', '温度')
    await fillInput(wrapper, '规则名', '高温警告')
    await fillInput(wrapper, '阈值', '35')
    await clickButton(wrapper, '保存')

    expect(createTemplateItem).toHaveBeenCalledTimes(1)
    const payload = createTemplateItem.mock.calls[0][1]
    expect(payload.ruleName).toBe('高温警告')
    expect(payload.threshold).toBe(35)
    expect(payload.ruleType).toBe('METRIC')
    expect(payload.pointId).toBe(5)
    expect(payload.mechanism).toEqual({
      breachCount: 0, rateLimit: null, rateWindowSec: 60, reAlarmSec: 0, hysteresis: 0,
    })
  })

  it('折叠"机制增强"时保存视为整组禁用（隐藏值不得静默带出）', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await clickButton(wrapper, '新增规则行')

    await selectOption(wrapper, '指标', '温度')

    // 展开并填入机制值
    const checkbox = wrapper.find('.el-checkbox')
    await checkbox.find('input').setValue(true)
    await fillInput(wrapper, '连续越限', '3')
    await fillInput(wrapper, '变化量限值', '5')
    // 再折叠
    await checkbox.find('input').setValue(false)

    await fillInput(wrapper, '规则名', '高温警告')
    await fillInput(wrapper, '阈值', '35')
    await clickButton(wrapper, '保存')

    const payload = createTemplateItem.mock.calls[0][1]
    expect(payload.mechanism).toEqual({
      breachCount: 0, rateLimit: null, rateWindowSec: 60, reAlarmSec: 0, hysteresis: 0,
    })
  })

  it('编辑已有规则：机制字段回填表单', async () => {
    const wrapper = mountDrawer([{
      id: 11, pointId: 5, pointCode: 'temperature', pointName: '温度', unit: '℃',
      ruleType: 'METRIC', ruleName: '高温警告', operator: 'GT', threshold: 35,
      alarmLevel: 2, enabled: 1, sortOrder: 0,
      breachCount: 3, rateLimit: 5, rateWindowSec: 60, reAlarmSec: 1800, hysteresis: 1.5,
    }])
    await flushPromises()

    await clickButton(wrapper, '编辑')

    // 展开态由任一机制激活驱动；输入框回填值校验
    const formItems = wrapper.findAll('.el-form-item')
    const breachItem = formItems.find(node => node.find('.el-form-item__label').text() === '连续越限')
    expect(numericInputValue(breachItem)).toBe(3)
    const rateItem = formItems.find(node => node.find('.el-form-item__label').text() === '变化量限值')
    expect(numericInputValue(rateItem)).toBe(5)
    const reAlarmItem = formItems.find(node => node.find('.el-form-item__label').text() === '再告警间隔')
    expect(numericInputValue(reAlarmItem)).toBe(1800)
  })

  it('启用开关的部分更新不携带 mechanism 组（后端整组保持语义）', async () => {
    const wrapper = mountDrawer([{
      id: 11, pointId: 5, pointCode: 'temperature', pointName: '温度',
      ruleType: 'METRIC', ruleName: '高温警告', operator: 'GT', threshold: 35,
      alarmLevel: 2, enabled: 1, sortOrder: 0,
      breachCount: 3, rateLimit: 5, rateWindowSec: 60, reAlarmSec: 0, hysteresis: 0,
    }])
    await flushPromises()

    const switc1 = wrapper.find('.rule-row .el-switch')
    await switc1.trigger('click')
    await flushPromises()

    expect(updateTemplateItem).toHaveBeenCalledTimes(1)
    const [itemId, payload] = updateTemplateItem.mock.calls[0]
    expect(itemId).toBe(11)
    expect(payload.enabled).toBe(false)
    expect(payload).not.toHaveProperty('mechanism')
  })

  it('校验：规则名为空不提交', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await clickButton(wrapper, '新增规则行')
    await fillInput(wrapper, '阈值', '35')
    await clickButton(wrapper, '保存')

    expect(createTemplateItem).not.toHaveBeenCalled()
  })

  it('校验：METRIC 未选指标不提交', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await clickButton(wrapper, '新增规则行')
    await fillInput(wrapper, '规则名', '高温警告')
    await fillInput(wrapper, '阈值', '35')
    await clickButton(wrapper, '保存')

    // pointId 默认 null
    expect(createTemplateItem).not.toHaveBeenCalled()
  })

  it('校验：阈值为空不提交', async () => {
    const wrapper = mountDrawer()
    await flushPromises()
    await clickButton(wrapper, '新增规则行')
    await fillInput(wrapper, '规则名', '高温警告')
    await clickButton(wrapper, '保存')

    expect(createTemplateItem).not.toHaveBeenCalled()
  })
})
