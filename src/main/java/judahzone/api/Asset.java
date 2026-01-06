package judahzone.api;

import java.io.File;

import judahzone.util.Recording;



public record Asset(String name, File file, Recording recording, long samples, Category category) {

public enum Category { DRUMS, STEPSAMPLE, SAMPLER, TRACK, USER} // PLAYER, SCOPE

}