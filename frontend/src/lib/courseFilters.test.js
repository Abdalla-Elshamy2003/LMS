import assert from 'node:assert/strict'
import test from 'node:test'
import { courseTeacherOptions } from './courseFilters.js'

test('managed academy teachers appear even when the staff endpoint lists only head-office users', () => {
  const courses = [{ teacherId: 5, teacherName: 'أ. محمد' }, { teacherId: 11, teacherName: 'محمد سليمان' }, { teacherId: 67, teacherName: 'محمد العازمي' }]
  const options = courseTeacherOptions(courses, [{ id: 5, fullName: 'أ. محمد' }])
  assert.deepEqual(new Set(options.map(t => t.id)), new Set(['5', '11', '67']))
  assert.equal(options.find(t => t.id === '67').name, 'محمد العازمي')
  assert.equal(courses.filter(c => String(c.teacherId) === '11').length, 1)
})

test('options keep distinct accounts with the same name, normalize IDs, and exclude courses without teachers', () => {
  const options = courseTeacherOptions([{ teacherId: 11, teacherName: 'أحمد' }, { teacherId: '11', teacherName: 'أحمد' }, { teacherId: 12, teacherName: 'أحمد' }, { teacherId: null }])
  assert.deepEqual(options, [{ id: '11', name: 'أحمد' }, { id: '12', name: 'أحمد' }])
  assert.deepEqual(courseTeacherOptions([]), [])
})
