import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

const SUMMARY_DESTINATION = '/user/queue/summary'
const summaryListeners = new Set()
const statusListeners = new Set()
const unreadCountListeners = new Set()

let client = null
let summarySubscription = null

function notifyStatus(connected) {
  statusListeners.forEach((listener) => listener(connected))
}

function notifySummary(summary) {
  summaryListeners.forEach((listener) => listener(summary))
}

function parseSummary(message) {
  try {
    const summary = JSON.parse(message.body)
    if (!summary?.resourceType || !summary?.resourceId || !summary?.eventType) return
    notifySummary(summary)
  } catch {
    // Invalid summaries are ignored; business state is always fetched through REST.
  }
}

export function connectSummarySocket() {
  if (client?.active || !localStorage.getItem('token')) return

  client = new Client({
    webSocketFactory: () => new SockJS('/api/ws'),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
    beforeConnect: () => {
      const token = localStorage.getItem('token')
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },
    onConnect: () => {
      summarySubscription?.unsubscribe()
      summarySubscription = client.subscribe(SUMMARY_DESTINATION, parseSummary)
      notifyStatus(true)
    },
    onDisconnect: () => notifyStatus(false),
    onStompError: () => notifyStatus(false),
    onWebSocketClose: () => notifyStatus(false),
  })
  client.activate()
}

export async function disconnectSummarySocket() {
  summarySubscription?.unsubscribe()
  summarySubscription = null
  if (client) {
    await client.deactivate()
    client = null
  }
  notifyStatus(false)
}

export function onSummary(listener) {
  summaryListeners.add(listener)
  return () => summaryListeners.delete(listener)
}

export function onSummaryConnectionChange(listener) {
  statusListeners.add(listener)
  return () => statusListeners.delete(listener)
}

export function publishUnreadNotificationCount(count) {
  unreadCountListeners.forEach((listener) => listener(Number(count) || 0))
}

export function onUnreadNotificationCountChange(listener) {
  unreadCountListeners.add(listener)
  return () => unreadCountListeners.delete(listener)
}
