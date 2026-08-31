import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

enableAutoUnmount(afterEach)

const pushSpy = vi.fn()
const replaceSpy = vi.fn()
let mockRoute = { params: { roomId: '3' }, name: 'room-overview' }

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: pushSpy, replace: replaceSpy }),
}))

vi.mock('@/api/cloudRoom', () => ({
  getCloudRoomById: vi.fn(),
  getRoomCredential: vi.fn(),
}))
vi.mock('@/api/alarmEvent', () => ({
  getAlarmEventPage: vi.fn(),
}))

import RoomWorkspace from '../RoomWorkspace.vue'
import { getAlarmEventPage } from '@/api/alarmEvent'
import { getCloudRoomById, getRoomCredential } from '@/api/cloudRoom'

/*
 * 测试目标：机房工作台——机房头信息与网关状态口径、未处理告警徽标、
 * tab 切换路由跳转、机房不存在防护（跳回驾驶舱）
 */

function mountWorkspace() {
  return mount(RoomWorkspace, {
    global: {
      plugins: [ElementPlus],
      stubs: { 'router-view': true },
    },
  })
}

describe('RoomWorkspace 机房工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute = { params: { roomId: '3' }, name: 'room-overview' }
    getCloudRoomById.mockResolvedValue({ data: { id: 3, roomName: '南昌一号机房', roomCode: 'NC-01' } })
    getRoomCredential.mockResolvedValue({ data: { lastSeenAt: new Date().toISOString() } })
    getAlarmEventPage.mockResolvedValue({ data: { total: 2, records: [] } })
  })

  it('加载机房信息：名称、网关在线口径（5 分钟内）、未处理告警徽标', async () => {
    const wrapper = mountWorkspace()
    await flushPromises()

    expect(getCloudRoomById).toHaveBeenCalledWith(3)
    expect(wrapper.find('h1').text()).toBe('南昌一号机房')
    expect(wrapper.text()).toContain('网关在线')
    expect(wrapper.find('.workspace-badge').text()).toContain('2')
  })

  it('网关 5 分钟内无心跳显示离线', async () => {
    getRoomCredential.mockResolvedValue({
      data: { lastSeenAt: new Date(Date.now() - 10 * 60 * 1000).toISOString() },
    })
    const wrapper = mountWorkspace()
    await flushPromises()

    expect(wrapper.text()).toContain('网关离线')
  })

  it('tab 切换路由跳转到对应工作台子路由', async () => {
    const wrapper = mountWorkspace()
    await flushPromises()

    const devicesTab = wrapper.findAll('.el-tabs__item')
      .find(node => node.text() === '设备与数据')
    await devicesTab.trigger('click')
    await flushPromises()

    expect(replaceSpy).toHaveBeenCalledWith({ name: 'room-devices', params: { roomId: 3 } })
  })

  it('机房不存在：提示并跳回驾驶舱', async () => {
    getCloudRoomById.mockRejectedValue(new Error('404'))
    mountWorkspace()
    await flushPromises()

    expect(replaceSpy).toHaveBeenCalledWith('/dashboard')
  })
})
