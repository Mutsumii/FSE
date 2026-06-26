package edu.zju.se.management.model;

public record Admin(long id, String username, String passwordHash, String role) {
}
