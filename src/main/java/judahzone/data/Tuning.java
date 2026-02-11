package judahzone.data;

import judahzone.api.Note;

public record Tuning(float frequency, float probability, Note note, float deviationHz) {}


