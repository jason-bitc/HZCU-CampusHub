function createRequestGate() {
  let generation = 0;
  return {
    next() {
      generation += 1;
      return generation;
    },
    current() {
      return generation;
    },
    isCurrent(token) {
      return token === generation;
    }
  };
}

module.exports = { createRequestGate };
