package com.allanvital.maestrao.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Singleton row holding SMTP + daily report settings.
 */
@Entity
@Table(name = "email_settings")
public class EmailSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Lob
    @Column(name = "smtp_encrypted_password")
    private String smtpEncryptedPassword;

    @Column(name = "smtp_use_starttls", nullable = false)
    private boolean smtpUseStartTls = true;

    @Column(name = "smtp_use_ssl", nullable = false)
    private boolean smtpUseSsl = false;

    @Column(name = "from_address", length = 255)
    private String fromAddress;

    @Column(name = "to_addresses", length = 2000)
    private String toAddresses;

    @Column(name = "subject_prefix", length = 255)
    private String subjectPrefix;

    @Column(name = "send_time")
    private LocalTime sendTime;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpEncryptedPassword() {
        return smtpEncryptedPassword;
    }

    public void setSmtpEncryptedPassword(String smtpEncryptedPassword) {
        this.smtpEncryptedPassword = smtpEncryptedPassword;
    }

    public boolean isSmtpUseStartTls() {
        return smtpUseStartTls;
    }

    public void setSmtpUseStartTls(boolean smtpUseStartTls) {
        this.smtpUseStartTls = smtpUseStartTls;
    }

    public boolean isSmtpUseSsl() {
        return smtpUseSsl;
    }

    public void setSmtpUseSsl(boolean smtpUseSsl) {
        this.smtpUseSsl = smtpUseSsl;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddresses() {
        return toAddresses;
    }

    public void setToAddresses(String toAddresses) {
        this.toAddresses = toAddresses;
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }

    public LocalTime getSendTime() {
        return sendTime;
    }

    public void setSendTime(LocalTime sendTime) {
        this.sendTime = sendTime;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
