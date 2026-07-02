package com.gemono.backend.service;

public interface EmailService {
    void sendMagicLink(String toEmail, String magicUrl, int expiryMinutes);
}