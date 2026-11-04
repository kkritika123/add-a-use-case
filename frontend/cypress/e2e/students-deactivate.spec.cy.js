// UC-STU-deactivate-student: the Deactivate control in the student edit dialog. The student API is stubbed, so these
// tests check what the dialog shows and sends; the backend's own tests cover who may deactivate and what changes.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const john = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const ok = (message, data) => ({ flag: true, code: 200, message, data })

const page = (content) =>
  ok('Find students successfully', {
    content,
    number: 0,
    size: 50,
    totalElements: content.length,
    totalPages: 1
  })

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', { body: page([john]) }).as('search')
    cy.intercept('GET', '**/students/4', { body: ok('Find student successfully', john) }).as(
      'student'
    )
    cy.intercept('PATCH', '**/students/4/deactivate', {
      body: ok('Deactivate student successfully', { ...john, enabled: false })
    }).as('deactivate')
  })

  function openStudent() {
    cy.visit('/students')
    cy.wait('@search')
    cy.get('.el-table__row').contains('Smith').parents('.el-table__row').find('button').click()
    cy.wait('@student')
    cy.get('[data-cy=student-status]').should('contain', 'Active')
  }

  function openConfirmation() {
    cy.get('[data-cy=student-deactivate]').click()
    cy.get('.deactivate-student-confirm').should('be.visible')
  }

  it('warns of the consequences and asks for confirmation (step 4)', () => {
    openStudent()
    openConfirmation()

    cy.get('.deactivate-student-confirm')
      .should('contain', 'Deactivate John Smith?')
      .and(
        'contain',
        'no longer be able to log in or submit weekly activity reports and peer evaluations'
      )
      .and('contain', 'The account and all submitted work are kept')
      .and('contain', 'stays on the team, marked inactive')
      .and('contain', 'Teammates will no longer owe or be able to submit peer evaluations')
      .and('contain', 'takes effect immediately')
      .and('contain', 'You can reactivate the student later')
      .and('contain', 'This is not deletion')
  })

  it('sends nothing and keeps the student active when cancelled (5a)', () => {
    openStudent()
    openConfirmation()

    cy.get('.deactivate-student-confirm').contains('button', 'Cancel').click()

    cy.get('.deactivate-student-confirm').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
    cy.get('[data-cy=student-status]').should('contain', 'Active')
    cy.get('[data-cy=student-deactivate]').should('be.visible')
  })

  it('deactivates on confirm and shows the new status (steps 5-6)', () => {
    openStudent()
    openConfirmation()

    cy.get('.deactivate-student-confirm').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.contains('.el-message', 'John Smith has been deactivated').should('be.visible')
    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
    cy.get('[data-cy=student-reactivate]').should('be.visible')
  })

  it('shows the alert and the current status when the student was already deactivated (3b)', () => {
    cy.intercept('PATCH', '**/students/4/deactivate', {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'The student is already deactivated.' }
    }).as('conflict')
    openStudent()
    // The reload after the conflict finds the student deactivated
    cy.intercept('GET', '**/students/4', {
      body: ok('Find student successfully', { ...john, enabled: false })
    }).as('reloaded')
    openConfirmation()

    cy.get('.deactivate-student-confirm').contains('button', 'Deactivate').click()

    cy.wait('@conflict')
    cy.contains('.el-message', 'The student is already deactivated.').should('be.visible')
    cy.wait('@reloaded')
    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-reactivate]').should('be.visible')
  })
})
