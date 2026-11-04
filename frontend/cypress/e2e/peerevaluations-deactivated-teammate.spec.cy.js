// BR-inactive-evaluatee (UC-STU-deactivate-student): the Submit Evaluations form offers no new evaluation of a
// deactivated teammate, but still shows an evaluation of her that was saved before she was deactivated.
// The API is stubbed; the backend's own tests cover the refusal of a new evaluation and the allowed edit.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const ok = (message, data) => ({ flag: true, code: 200, message, data })

const teammate = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName.toLowerCase()}@abc.edu`,
  enabled,
  roles: 'student',
  sectionId: 2,
  teamId: 1,
  teamName: 'Team1'
})

const team = [
  teammate(4, 'John', 'Smith', true),
  teammate(5, 'Eric', 'Hudson', false), // deactivated
  teammate(6, 'Jerry', 'Moon', true)
]

const criterion = {
  criterionId: 1,
  criterion: 'Quality of work',
  description: 'Quality',
  maxScore: 10
}

const saved = (evaluationId, evaluateeId, evaluateeName) => ({
  evaluationId,
  week: '2023-W32',
  evaluatorId: 4,
  evaluatorName: 'John Smith',
  evaluateeId,
  evaluateeName,
  ratings: [
    { ratingId: evaluationId, criterionId: 1, criterion: 'Quality of work', actualScore: 8 }
  ],
  totalScore: 8,
  publicComment: '',
  privateComment: ''
})

describe('Submit peer evaluations with a deactivated teammate', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', '**/students/teams/1', {
      body: ok('Find students by team id successfully', team)
    })
    cy.intercept('GET', '**/sections/2', {
      body: ok('Find section successfully', { id: 2, sectionId: 2, rubricId: 1 })
    })
    cy.intercept('GET', '**/rubrics/1', {
      body: ok('Find rubric successfully', { rubricId: 1, criteria: [criterion] })
    })
    cy.intercept('POST', '**/evaluations', (req) =>
      req.reply({ body: ok('Add peer evaluation successfully', { ...req.body, evaluationId: 99 }) })
    ).as('submit')
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', { body: ok('Receipt sent', null) })
    cy.intercept('PUT', '**/evaluations/*', (req) =>
      req.reply({ body: ok('Update peer evaluation successfully', req.body) })
    ).as('update')
  })

  it('leaves the deactivated teammate out of a new submission', () => {
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', {
      body: ok('Get peer evaluations', [])
    })
    cy.visit('/evaluations/submit-evaluations')

    cy.get('.el-table__body')
      .should('contain', 'John Smith')
      .and('contain', 'Jerry Moon')
      .and('not.contain', 'Eric Hudson')
    cy.contains('button', 'Submit').click()
    cy.wait('@submit')
    cy.wait('@submit')
    cy.get('@submit.all').then((calls) => {
      expect(calls.map((call) => call.request.body.evaluateeId)).to.have.members([4, 6])
    })
  })

  it('still shows and updates an evaluation of her saved before she was deactivated', () => {
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', {
      body: ok('Get peer evaluations', [
        saved(1, 4, 'John Smith'),
        saved(2, 5, 'Eric Hudson'),
        saved(3, 6, 'Jerry Moon')
      ])
    })
    cy.visit('/evaluations/submit-evaluations')

    cy.get('.el-table__body').should('contain', 'Eric Hudson')
    cy.contains('button', 'Update').click()
    cy.wait('@update')
    cy.get('@update.all').should('have.length', 3)
    cy.get('@update.all').then((calls) => {
      expect(calls.map((call) => call.request.url)).to.satisfy((urls) =>
        urls.some((url) => url.endsWith('/evaluations/2'))
      )
    })
    cy.get('@submit.all').should('have.length', 0)
  })
})
