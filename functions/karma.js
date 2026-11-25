/**
 * Karma rules for CampusSync.
 *
 * Pure functions — no Firebase imports — so `node tools/karma-test.js` can exercise the award
 * decisions without the emulator. The Firestore triggers in index.js turn the returned entries
 * into read-only `karmaAwards/{awardId}` documents.
 *
 * Fraud and duplicate protection comes from three properties:
 *   1. the award id is derived from the contribution (`<sourceType>_<sourceId>`), so a second
 *      award for the same contribution reuses the id and the transaction refuses to write it;
 *   2. the amount is fixed here, never taken from a document;
 *   3. the backend only awards a contribution that is genuinely completed (an accepted answer, a
 *      completed request, a resource with enough good ratings) and never awards an account for
 *      its own contribution.
 */

const KARMA_AMOUNTS = {
  answer_accepted: 15,
  resource_helpful: 10,
  tutor_review: 10,
};

/** A resource becomes "helpful" once it has this many ratings and this average. */
const RESOURCE_MIN_RATINGS = 3;
const RESOURCE_MIN_AVERAGE = 4;

/** A tutoring review rewards the tutor from this rating upwards. */
const TUTOR_REVIEW_MIN_RATING = 4;

function awardIdFor(sourceType, sourceId) {
  if (!sourceType || !sourceId) return null;
  return `${sourceType}_${sourceId}`;
}

function noteFor(sourceType, extra) {
  switch (sourceType) {
    case "answer_accepted":
      return `Answer accepted${extra ? ` on “${extra}”` : ""}`;
    case "resource_helpful":
      return `Well-rated resource${extra ? ` “${extra}”` : ""}`;
    case "tutor_review":
      return "Positive tutoring review";
    default:
      return "Helpful contribution";
  }
}

function entry(sourceType, sourceId, recipientId, note) {
  const awardId = awardIdFor(sourceType, sourceId);
  if (!awardId || !recipientId) return null;
  return {
    awardId,
    recipientId,
    sourceType,
    sourceId,
    amount: KARMA_AMOUNTS[sourceType],
    note: noteFor(sourceType, note),
  };
}

/**
 * The doubt was marked solved with an accepted answer: reward the answer's author.
 * Nothing is awarded when the asker accepted their own answer.
 */
function answerAcceptedAward({ doubtId, answerId, answerAuthorId, doubtAuthorId, doubtTitle }) {
  if (!doubtId || !answerId || !answerAuthorId) return null;
  if (doubtAuthorId && answerAuthorId === doubtAuthorId) return null;
  return entry("answer_accepted", answerId, answerAuthorId, doubtTitle);
}

/** The resource crossed the helpfulness threshold: reward its uploader. */
function resourceHelpfulAward({ resourceId, uploaderId, ratingSum, ratingCount, title }) {
  if (!resourceId || !uploaderId) return null;
  const count = Number(ratingCount) || 0;
  const sum = Number(ratingSum) || 0;
  if (count < RESOURCE_MIN_RATINGS) return null;
  if (sum / count < RESOURCE_MIN_AVERAGE) return null;
  return entry("resource_helpful", resourceId, uploaderId, title);
}

/** A completed tutoring session was reviewed well: reward the tutor. */
function tutorReviewAward({ requestId, tutorId, studentId, status, reviewRating }) {
  if (!requestId || !tutorId) return null;
  if (status !== "completed") return null;
  const rating = Number(reviewRating) || 0;
  if (rating < TUTOR_REVIEW_MIN_RATING || rating > 5) return null;
  if (studentId && studentId === tutorId) return null;
  return entry("tutor_review", requestId, tutorId);
}

module.exports = {
  KARMA_AMOUNTS,
  RESOURCE_MIN_RATINGS,
  RESOURCE_MIN_AVERAGE,
  TUTOR_REVIEW_MIN_RATING,
  awardIdFor,
  answerAcceptedAward,
  resourceHelpfulAward,
  tutorReviewAward,
};
