import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

enableAutoUnmount(afterEach)

const pushSpy = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushSpy }),
}))

vi.mock('@/api/notification', () => ({
  getNotificationPage: vi.fn(),
  markAllNotificationsRead: vi.fn().mockResolvedValue({ data: null }),
  markNotificationRead: vi.fn().mockResolvedValue({ data: null }),
}))
vi.mock('@/api/realtime', () => ({
  publishUnreadNotificationCount: vi.fn(),
}))

import NotificationModal from '../NotificationModal.vue'
import {
  getNotificationPage,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { publishUnreadNotificationCount } from '@/api/realtime'

/*
 * 测试目标：站内通知弹窗——打开时拉取列表、未读标记已读 + 未读数广播、
 * 告警类通知跳转告警详情、全部已读
 */

const ITEMS = [
  { id: 1, title: '新增告警', content: '高温警告', isRead: 0, sourceType: 'ALARM_EVENT', sourceId: '9', createTime: '2026-08-31 10:00:00' },
  { id: 2, title: '系统公告', content: '维护通知', isRead: 1, sourceType: 'SYSTEM', sourceId: null, createTime: '2026-08-31 09:00:00' },
]

function mountModal(props = {}) {
  return mount(NotificationModal, {
    props: { visible: false, unreadCount: 1, ...props },
    global: {
      plugins: [ElementPlus],
      stubs: {
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
      },
    },
  })
}

// visible 拉取依赖 watch 变化：统一以 false 挂载后置 true 触发
async function mountAndOpen(props = {}) {
  const wrapper = mountModal(props)
  await wrapper.setProps({ visible: true })
  await flushPromises()
  return wrapper
}

describe('NotificationModal 站内通知', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 每例深拷贝：组件会把 item.isRead 原地置 1，避免跨测试变异
    getNotificationPage.mockImplementation(async () => ({
      data: { records: ITEMS.map(item => ({ ...item })), total: ITEMS.length },
    }))
  })

  it('visible 打开时拉取第一页列表并渲染未读标记', async () => {
    const wrapper = await mountAndOpen()

    expect(getNotificationPage).toHaveBeenCalledWith({ current: 1, size: 10 })
    const noticeItems = wrapper.findAll('.notice-item')
    expect(noticeItems).toHaveLength(2)
    expect(noticeItems[0].classes()).toContain('unread')
  })

  it('过滤未读时携带 isRead=0 参数', async () => {
    const wrapper = await mountAndOpen()
    getNotificationPage.mockClear()

    const unreadRadio = wrapper.findAll('.el-radio-button').find(n => n.text() === '未读')
    await unreadRadio.find('input').setValue()
    await flushPromises()

    expect(getNotificationPage).toHaveBeenCalledWith({ current: 1, size: 10, isRead: 0 })
  })

  it('点击未读告警类通知：标记已读、广播未读数、跳转告警详情', async () => {
    const wrapper = await mountAndOpen({ unreadCount: 3 })

    await wrapper.findAll('.notice-item')[0].trigger('click')
    await flushPromises()

    expect(markNotificationRead).toHaveBeenCalledWith(1)
    expect(publishUnreadNotificationCount).toHaveBeenCalledWith(2)
    expect(pushSpy).toHaveBeenCalledWith('/alerts?id=9')
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('read')).toBeTruthy()
  })

  it('点击已读系统通知：不跳转不重复标记', async () => {
    const wrapper = await mountAndOpen()

    await wrapper.findAll('.notice-item')[1].trigger('click')
    await flushPromises()

    expect(markNotificationRead).not.toHaveBeenCalled()
    expect(pushSpy).not.toHaveBeenCalled()
    expect(publishUnreadNotificationCount).not.toHaveBeenCalled()
  })

  it('全部已读：调接口、广播 0、刷新列表', async () => {
    const wrapper = await mountAndOpen({ unreadCount: 2 })

    const markAllBtn = wrapper.findAll('button').find(n => n.text().includes('全部已读'))
    await markAllBtn.trigger('click')
    await flushPromises()
    await flushPromises()

    expect(markAllNotificationsRead).toHaveBeenCalled()
    expect(publishUnreadNotificationCount).toHaveBeenCalledWith(0)
    expect(wrapper.emitted('read')).toBeTruthy()
    expect(getNotificationPage).toHaveBeenCalledTimes(2)
  })
})
