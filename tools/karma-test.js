#!/usr/bin/env node
/**
 * Karma award rules, tested without Firebase.
 *
 *   node tools/karma-test.js
 *
 * The backend keeps karma in a read-only ledger whose document id is derived from the
 * contribution. These checks cover the three properties that make duplicate rewards and
 * self-awards impossible: deterministic ids, fixed amounts and completed-contribution checks.
 */

const {
  KARMA_AMOUNTS,
  answerAcceptedAward,
  resourceHelpfulAward,
  tutorReviewAward,
} = require("../functions/karma");

let passed = 0;
let failed = 0;

function check(name, condition) {
  if (condition) {
    passed += 1;
    console.log(`  PASS  ${name}`);
  } else {
    failed += 1;
    console.log(`  FAIL  ${name}`);
  }
}

console.log("\nA. fixed amounts");
check("an accepted answer is worth 15", KARMA_AMOUNTS.answer_accepted === 15);
check("a helpful resource is worth 10", KARMA_AMOUNTS.resource_helpful === 10);
check("a good tutoring review is worth 10", KARMA_AMOUNTS.tutor_review === 10);

console.log("\nB. accepted answers");
const accepted = answerAcceptedAward({
  doubtId: "doubt1",
  answerId: "answer7",
  answerAuthorId: "tutor1",
  doubtAuthorId: "student1",
  doubtTitle: "Graphs",
});
check("an answer by another student earns karma", accepted !== null);
check("the recipient is the answer's author", accepted && accepted.recipientId === "tutor1");
check("the award amount is the fixed 15", accepted && accepted.amount === 15);
check("the award id is derived from the answer",
  accepted && accepted.awardId === "answer_accepted_answer7");
check("a repeat of the same contribution maps to the same id",
  answerAcceptedAward({
    doubtId: "doubt1",
    answerId: "answer7",
    answerAuthorId: "tutor1",
    doubtAuthorId: "student1",
  }).awardId === accepted.awardId);
check("a different answer maps to a different id",
  answerAcceptedAward({
    doubtId: "doubt1",
    answerId: "answer8",
    answerAuthorId: "tutor1",
    doubtAuthorId: "student1",
  }).awardId !== accepted.awardId);
check("accepting your own answer earns nothing",
  answerAcceptedAward({
    doubtId: "doubt1",
    answerId: "answer9",
    answerAuthorId: "student1",
    doubtAuthorId: "student1",
  }) === null);
check("an answer without an author earns nothing",
  answerAcceptedAward({ doubtId: "doubt1", answerId: "answer10" }) === null);
check("the amount cannot be overridden by the caller",
  answerAcceptedAward({
    doubtId: "doubt1",
    answerId: "answer7",
    answerAuthorId: "tutor1",
    doubtAuthorId: "student1",
    amount: 9999,
  }).amount === 15);

console.log("\nC. helpful resources");
const helpful = resourceHelpfulAward({
  resourceId: "res1",
  uploaderId: "uploader1",
  ratingSum: 13,
  ratingCount: 3,
  title: "Trees",
});
check("three ratings averaging above 4 earns karma", helpful !== null);
check("the uploader is the recipient", helpful && helpful.recipientId === "uploader1");
check("the award id is derived from the resource",
  helpful && helpful.awardId === "resource_helpful_res1");
check("two ratings are not enough",
  resourceHelpfulAward({
    resourceId: "res2", uploaderId: "uploader1", ratingSum: 10, ratingCount: 2,
  }) === null);
check("a mediocre average earns nothing",
  resourceHelpfulAward({
    resourceId: "res3", uploaderId: "uploader1", ratingSum: 9, ratingCount: 3,
  }) === null);
check("an unrated resource earns nothing",
  resourceHelpfulAward({
    resourceId: "res4", uploaderId: "uploader1", ratingSum: 0, ratingCount: 0,
  }) === null);
check("a resource without an uploader earns nothing",
  resourceHelpfulAward({ resourceId: "res5", ratingSum: 15, ratingCount: 3 }) === null);

console.log("\nD. tutoring reviews");
const reviewed = tutorReviewAward({
  requestId: "req1",
  tutorId: "tutor1",
  studentId: "student1",
  status: "completed",
  reviewRating: 5,
});
check("a completed five-star session earns karma", reviewed !== null);
check("the tutor is the recipient", reviewed && reviewed.recipientId === "tutor1");
check("the award id is derived from the request",
  reviewed && reviewed.awardId === "tutor_review_req1");
check("a three-star review earns nothing",
  tutorReviewAward({
    requestId: "req2", tutorId: "tutor1", studentId: "student1",
    status: "completed", reviewRating: 3,
  }) === null);
check("an unfinished request earns nothing",
  tutorReviewAward({
    requestId: "req3", tutorId: "tutor1", studentId: "student1",
    status: "accepted", reviewRating: 5,
  }) === null);
check("a review cannot be issued twice under a different id",
  tutorReviewAward({
    requestId: "req1", tutorId: "tutor1", studentId: "student1",
    status: "completed", reviewRating: 5, awardId: "tutor_review_other",
  }).awardId === "tutor_review_req1");
check("an impossible rating earns nothing",
  tutorReviewAward({
    requestId: "req4", tutorId: "tutor1", studentId: "student1",
    status: "completed", reviewRating: 9,
  }) === null);

console.log(`\n${passed}/${passed + failed} checks passed`);
if (failed > 0) {
  console.log("\nKarma tests FAILED");
  process.exit(1);
}
console.log("KARMA_TESTS_OK");
