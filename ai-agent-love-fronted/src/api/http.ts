import axios from 'axios'

export const API_BASE_URL = import.meta.env.MODE === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
})

export default httpClient
