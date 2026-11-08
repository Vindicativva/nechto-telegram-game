import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PlayerCountStepper } from './PlayerCountStepper'

describe('PlayerCountStepper', () => {
  it('disables the decrement button at the minimum of 4', () => {
    render(<PlayerCountStepper value={4} onChange={vi.fn()} />)
    expect(screen.getByLabelText('Уменьшить количество участников')).toBeDisabled()
    expect(screen.getByLabelText('Увеличить количество участников')).toBeEnabled()
  })

  it('disables the increment button at the maximum of 12', () => {
    render(<PlayerCountStepper value={12} onChange={vi.fn()} />)
    expect(screen.getByLabelText('Увеличить количество участников')).toBeDisabled()
    expect(screen.getByLabelText('Уменьшить количество участников')).toBeEnabled()
  })

  it('calls onChange with the next value within range', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<PlayerCountStepper value={6} onChange={onChange} />)

    await user.click(screen.getByLabelText('Увеличить количество участников'))
    expect(onChange).toHaveBeenCalledWith(7)

    await user.click(screen.getByLabelText('Уменьшить количество участников'))
    expect(onChange).toHaveBeenCalledWith(5)
  })
})
