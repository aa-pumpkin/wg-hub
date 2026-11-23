/**
 * Picks who is responsible for the next occurrence.
 *
 * - Normally: the person after the previous assignee, in rotation order.
 * - No previous assignee, or they left the rotation: the person with the least to do
 *   right now (ties go to rotation order). Three chores created for three people
 *   therefore start on three different people, and then rotate in step:
 *
 *     Week 1: Anna Kitchen, Max Bathroom, Lisa Trash
 *     Week 2: Max Kitchen,  Lisa Bathroom, Anna Trash
 */
export function pickAssignee(
  rotation: readonly string[],
  previousAssigneeId: string | null,
  workload: ReadonlyMap<string, number>,
): string | null {
  if (rotation.length === 0) return null

  const previousIndex = previousAssigneeId ? rotation.indexOf(previousAssigneeId) : -1
  if (previousIndex >= 0) return rotation[(previousIndex + 1) % rotation.length]!

  let best = rotation[0]!
  for (const userId of rotation) {
    if ((workload.get(userId) ?? 0) < (workload.get(best) ?? 0)) best = userId
  }
  return best
}

/**
 * The circle for main tasks. People sit in a fixed clockwise order and every week each
 * task moves on to the next person:
 *
 *            Kitchen  Bathroom  Trash
 *   Week 0:  Anna     Max       Lisa
 *   Week 1:  Lisa     Anna      Max     <- each person moves on to the next task
 *   Week 2:  Max      Lisa      Anna
 *
 * With more tasks than people, some get two; with fewer, someone has a free week. Either
 * way it evens out, because it depends only on the week and the positions.
 */
export function circleAssignee(
  circle: readonly string[],
  taskIndex: number,
  weekIndex: number,
): string | null {
  if (circle.length === 0) return null
  const size = circle.length
  const seat = (((taskIndex - weekIndex) % size) + size) % size
  return circle[seat]!
}
