<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:">
      <el-tag v-if="studentBeingEdited.enabled" type="success" data-cy="student-status">Active</el-tag>
      <el-tag v-else type="danger" data-cy="student-status">Deactivated</el-tag>
      <!-- UC-STU-deactivate-student. Separate from Save: neither saves nor discards unsaved edits. -->
      <template v-if="userInfoStore.isInstructor">
        <el-button
          v-if="studentBeingEdited.enabled"
          type="danger"
          plain
          size="small"
          style="margin-left: 12px"
          :loading="statusChanging"
          data-cy="student-deactivate"
          @click="confirmAndDeactivate()"
        >
          Deactivate
        </el-button>
        <!-- Interim reactivation until UC-STU-reactivate-student brings its own endpoint -->
        <el-button
          v-else
          type="success"
          plain
          size="small"
          style="margin-left: 12px"
          :loading="statusChanging"
          data-cy="student-reactivate"
          @click="reactivate()"
        >
          Reactivate
        </el-button>
      </template>
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { useUserInfoStore } from '@/stores/userInfo'
import { ref, onMounted } from 'vue'
import axios from 'axios'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

const userInfoStore = useUserInfoStore()

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

// The student as last loaded from the server. Reactivate sends these values, so the form's unsaved edits are not
// saved by it, and a status change updates only the status, so they are not discarded either.
let loadedStudent: Student | null = null

const statusChanging = ref(false)

const studentForm = ref<FormInstance>() // Form reference

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)
  loadedStudent = result.data

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
}

// Refresh only the status, keeping whatever the user has typed into the other fields
async function reloadStatus() {
  const result = await findStudentById(studentBeingEdited.value.id as number)
  loadedStudent = result.data
  studentBeingEdited.value.enabled = result.data.enabled
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

const DEACTIVATION_CONSEQUENCES =
  'This student will no longer be able to log in or submit weekly activity reports and peer evaluations. ' +
  'The account and all submitted work are kept, and the student stays on the team, marked inactive. ' +
  'Teammates will no longer owe or be able to submit peer evaluations of this student. ' +
  'This takes effect immediately, even if the student is logged in now. ' +
  'You can reactivate the student later. ' +
  "This is not deletion, which permanently removes a student and the student's work."

// UC-STU-deactivate-student steps 3-6, with 5a (cancel) and 3b (already deactivated)
async function confirmAndDeactivate() {
  const name = `${loadedStudent?.firstName} ${loadedStudent?.lastName}`
  try {
    await ElMessageBox.confirm(DEACTIVATION_CONSEQUENCES, `Deactivate ${name}?`, {
      type: 'warning',
      confirmButtonText: 'Deactivate',
      cancelButtonText: 'Cancel',
      customClass: 'deactivate-student-confirm'
    })
  } catch {
    return // 5a: closed without saving any change; the student remains active
  }

  statusChanging.value = true
  try {
    const result = await deactivateStudent(studentBeingEdited.value.id as number)
    loadedStudent = result.data
    studentBeingEdited.value.enabled = result.data.enabled
    ElMessage.success(`${name} has been deactivated`)
  } catch (error) {
    // 3b: the shared handler has already shown "The student is already deactivated."; show the current status.
    // A 403 (3a) has already routed to the forbidden page.
    if (axios.isAxiosError(error) && error.response?.status === 409) {
      await reloadStatus()
    }
  } finally {
    statusChanging.value = false
  }
}

async function reactivate() {
  if (!loadedStudent) return
  statusChanging.value = true
  try {
    const result = await updateStudent({ ...loadedStudent, enabled: true })
    loadedStudent = result.data
    studentBeingEdited.value.enabled = result.data.enabled
    ElMessage.success(`${result.data.firstName} ${result.data.lastName} has been reactivated`)
  } finally {
    statusChanging.value = false
  }
}

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  // No `enabled`: Save never changes the status (a stale form could otherwise reactivate); only Reactivate does
  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}
</script>

<style scoped lang="scss"></style>
