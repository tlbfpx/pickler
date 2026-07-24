// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestPost = vi.fn()
vi.mock('./request', () => ({
  default: { post: (...args: unknown[]) => requestPost(...args) }
}))

beforeEach(() => {
  requestPost.mockReset()
})

import { uploadFile } from './files'

describe('uploadFile', () => {
  it('posts to /files/upload with FormData and multipart header', () => {
    requestPost.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    uploadFile(file)
    expect(requestPost).toHaveBeenCalledTimes(1)
    const [url, body, config] = requestPost.mock.calls[0]
    expect(url).toBe('/files/upload')
    expect(body).toBeInstanceOf(FormData)
    // FormData 应当包含 file
    const appendedFile = (body as FormData).get('file')
    expect(appendedFile).toBeInstanceOf(File)
    expect((appendedFile as File).name).toBe('test.txt')
    expect((appendedFile as File).type).toBe('text/plain')
    // config.headers
    expect(config).toMatchObject({
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { url: 'https://x.com/uploaded.jpg', filename: 'x.jpg' } }
    requestPost.mockReturnValue(Promise.resolve(fake))
    const file = new File(['x'], 'x.jpg', { type: 'image/jpeg' })
    const result = await uploadFile(file)
    expect(result).toBe(fake)
  })
})