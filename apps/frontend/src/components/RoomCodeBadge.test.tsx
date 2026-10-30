import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RoomCodeBadge } from './RoomCodeBadge'

describe('RoomCodeBadge', () => {
  it('copies the code to the clipboard and shows a confirmation', async () => {
    const user = userEvent.setup()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })

    render(<RoomCodeBadge code="X7K9M2" />)

    expect(screen.getByText('X7K9M2')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Копировать' }))

    expect(writeText).toHaveBeenCalledWith('X7K9M2')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Скопировано' })).toBeInTheDocument())
  })
})
