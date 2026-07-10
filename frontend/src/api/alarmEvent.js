import request from '@/utils/request'

export function getAlarmEventPage(params) {
  return request.get('/alarm-events/page', { params })
}

export function getAlarmEvent(id) {
  return request.get(`/alarm-events/${id}`)
}

export function acknowledgeAlarmEvent(id, data) {
  return request.post(`/alarm-events/${id}/ack`, data)
}

export function startHandlingAlarmEvent(id, data) {
  return request.post(`/alarm-events/${id}/handling`, data)
}

export function recoverAlarmEvent(id, data) {
  return request.post(`/alarm-events/${id}/recover`, data)
}

export function closeAlarmEvent(id, data) {
  return request.post(`/alarm-events/${id}/close`, data)
}

export function getAlarmEventTransitions(id) {
  return request.get(`/alarm-events/${id}/transitions`)
}

export function getAlarmEventHandlingRecords(id) {
  return request.get(`/alarm-events/${id}/handling-records`)
}
