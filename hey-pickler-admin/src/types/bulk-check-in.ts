// Loop-v15 — types matching backend BulkCheckInResult (PR #34).
// Mirrors /hey-pickler-server/.../vo/BulkCheckInResult.java.

export interface BulkCheckInRequest {
  registrationIds: number[]
}

export interface BulkCheckInResult {
  eventId: number
  requested: number
  updated: number
  updatedRegistrationIds: number[]
  skipped: {
    notFound: number[]
    withdrawn: number[]
  }
}
