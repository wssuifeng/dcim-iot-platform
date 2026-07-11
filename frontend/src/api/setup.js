import request from '@/utils/request'

export const getSetupStatus = () => request.get('/setup/status')
export const sendSetupCode = (email) => request.post('/setup/verification-codes', { email })
export const initializePlatformAdmin = (data) => request.post('/setup/platform-admin', data)
