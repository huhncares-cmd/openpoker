package de.openpoker.common.model;

import java.io.Serializable;

public record Card(Suit suit, Rank rank) implements Serializable {
}
