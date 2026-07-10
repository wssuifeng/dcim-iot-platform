import request from '@/utils/request'

export function getSimulationScenePage(params) {
  return request.get('/simulation/scenes/page', { params })
}

export function getSimulationScene(id) {
  return request.get(`/simulation/scenes/${id}`)
}

export function createSimulationScene(data) {
  return request.post('/simulation/scenes', data)
}

export function updateSimulationScene(id, data) {
  return request.put(`/simulation/scenes/${id}`, data)
}

export function deleteSimulationScene(id) {
  return request.delete(`/simulation/scenes/${id}`)
}

export function updateSimulationSceneStatus(id, status) {
  return request.put(`/simulation/scenes/${id}/status`, null, { params: { status } })
}

export function getSimulationSceneSteps(id) {
  return request.get(`/simulation/scenes/${id}/steps`)
}

export function addSimulationSceneStep(id, data) {
  return request.post(`/simulation/scenes/${id}/steps`, data)
}

export function deleteSimulationSceneStep(stepId) {
  return request.delete(`/simulation/scenes/steps/${stepId}`)
}

export function getSimulationRunPage(params) {
  return request.get('/simulation/runs/page', { params })
}

export function getSimulationRun(id) {
  return request.get(`/simulation/runs/${id}`)
}

export function startSimulationRun(data) {
  return request.post('/simulation/runs', data)
}

export function cancelSimulationRun(id) {
  return request.post(`/simulation/runs/${id}/cancel`)
}

export function getSimulationRunEvents(id) {
  return request.get(`/simulation/runs/${id}/events`)
}
