package com.hrms.service;

import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    // Simple regex to filter out obviously invalid / placeholder email addresses
    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        // Must contain exactly one @ and at least one dot after it
        String[] parts = email.split("@");
        if (parts.length != 2) return false;
        String domain = parts[1];
        // Reject internal/placeholder domains that are not real external mailboxes
        if (!domain.contains(".")) return false;
        if (domain.equalsIgnoreCase("company.com") || domain.equalsIgnoreCase("localhost")) return false;
        return true;
    }

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Sends the auto-generated login credentials to the new employee's personal email.
     *
     * @param toEmail        the employee's personal email address
     * @param employeeName   full display name
     * @param username       generated username
     * @param rawPassword    generated plain-text password (before encoding)
     */
    @Async
    public void sendCredentialsEmail(String toEmail, String employeeName, String username, String rawPassword) {
        if (!isValidEmail(toEmail)) {
            System.err.println("[EmailService] Skipping credentials email - invalid/placeholder address: " + toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, "D Tech HRIS"));
            helper.setTo(toEmail);
            helper.setReplyTo(fromEmail);
            helper.setSubject("Welcome to HR Portal - Your Login Credentials");
            message.setHeader("X-Mailer", "D Tech HRIS Mailer");
            message.setHeader("Precedence", "bulk");

            String htmlBody = buildCredentialsEmailHtml(employeeName, username, rawPassword);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            System.out.println("[EmailService] Credentials email sent successfully to " + toEmail);
        } catch (Exception e) {
            System.err.println("[EmailService] Failed to send credentials email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildCredentialsEmailHtml(String name, String username, String password) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:Inter,Arial,sans-serif;background:#f4f6fb;margin:0;padding:0;}"
                + ".wrap{max-width:540px;margin:40px auto;background:white;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.10);}"
                + ".header{background:linear-gradient(135deg,#6366f1,#ec4899);padding:36px 40px;text-align:center;}"
                + ".header h1{color:white;margin:0;font-size:24px;font-weight:800;letter-spacing:-0.5px;}"
                + ".header p{color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;}"
                + ".body{padding:36px 40px;}"
                + ".greeting{font-size:18px;font-weight:700;color:#1f2937;margin-bottom:8px;}"
                + ".intro{color:#6b7280;font-size:14px;line-height:1.6;margin-bottom:28px;}"
                + ".cred-box{display:flex;gap:16px;margin-bottom:28px;}"
                + ".cred-item{flex:1;padding:20px;border-radius:12px;text-align:center;}"
                + ".cred-user{background:#f0f9ff;border:2px solid #bae6fd;}"
                + ".cred-pass{background:#fdf4ff;border:2px solid #e9d5ff;}"
                + ".cred-label{font-size:11px;text-transform:uppercase;letter-spacing:0.08em;font-weight:700;margin-bottom:8px;}"
                + ".cred-user .cred-label{color:#0369a1;}"
                + ".cred-pass .cred-label{color:#7c3aed;}"
                + ".cred-value{font-family:monospace;font-size:22px;font-weight:800;}"
                + ".cred-user .cred-value{color:#0c4a6e;}"
                + ".cred-pass .cred-value{color:#4c1d95;}"
                + ".warning{background:#fef3c7;border-left:4px solid #f59e0b;border-radius:8px;padding:14px 16px;font-size:13px;color:#92400e;margin-bottom:28px;line-height:1.6;}"
                + ".steps{background:#f8fafc;border-radius:12px;padding:20px 24px;margin-bottom:28px;}"
                + ".steps h3{font-size:14px;font-weight:700;color:#374151;margin:0 0 12px;}"
                + ".step{display:flex;align-items:flex-start;gap:10px;margin-bottom:10px;font-size:13px;color:#6b7280;}"
                + ".step-num{background:#6366f1;color:white;border-radius:50%;width:20px;height:20px;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;flex-shrink:0;margin-top:1px;}"
                + ".btn{display:block;background:linear-gradient(135deg,#6366f1,#4f46e5);color:white;text-align:center;padding:14px;border-radius:10px;text-decoration:none;font-weight:700;font-size:15px;margin-bottom:28px;}"
                + ".footer{background:#f8fafc;padding:20px 40px;text-align:center;font-size:12px;color:#9ca3af;border-top:1px solid #f0f0f0;}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='header'>"
                + "<h1>🏢 HR Portal</h1>"
                + "<p>Welcome to the team — your account is ready</p>"
                + "</div>"
                + "<div class='body'>"
                + "<div class='greeting'>Welcome, " + name + "! 👋</div>"
                + "<p class='intro'>Your employee account has been created on the HR Portal. Below are your initial login credentials. Please keep them safe and change your password upon first login.</p>"
                + "<div class='cred-box'>"
                + "<div class='cred-item cred-user'><div class='cred-label'>Username</div><div class='cred-value'>" + username + "</div></div>"
                + "<div class='cred-item cred-pass'><div class='cred-label'>Password</div><div class='cred-value'>" + password + "</div></div>"
                + "</div>"
                + "<div class='warning'>⚠️ <strong>Important:</strong> These are your one-time credentials. You will be asked to change your password and upload a profile photo on your first login. Do not share these credentials with anyone.</div>"
                + "<div class='steps'>"
                + "<h3>First Login Steps</h3>"
                + "<div class='step'><div class='step-num'>1</div><span>Visit the HR Portal login page and sign in with the credentials above</span></div>"
                + "<div class='step'><div class='step-num'>2</div><span>Change your password to something secure (min. 8 characters)</span></div>"
                + "<div class='step'><div class='step-num'>3</div><span>Upload your profile photo</span></div>"
                + "<div class='step'><div class='step-num'>4</div><span>Complete your profile details</span></div>"
                + "</div>"
                + "<a href='http://localhost:5173/login' class='btn'>→ Login to HR Portal</a>"
                + "</div>"
                + "<div class='footer'>"
                + "This is an automated message from the HR Portal system. Please do not reply to this email.<br/>"
                + "If you did not expect this email, please contact your HR department immediately."
                + "</div>"
                + "</div></body></html>";
    }

    /**
     * Sends the Excel-imported account credentials email using the user's specific template.
     */
    @Async
    public void sendExcelImportCredentialsEmail(String toEmail, String firstName, String username, String rawPassword) {
        if (!isValidEmail(toEmail)) {
            System.err.println("[EmailService] Skipping Excel import email - invalid/placeholder address: " + toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, "D Tech HRIS"));
            helper.setTo(toEmail);
            helper.setReplyTo(fromEmail);
            helper.setSubject("Your System Account Has Been Created");
            message.setHeader("X-Mailer", "D Tech HRIS Mailer");

            String htmlBody = buildExcelImportEmailHtml(firstName, username, rawPassword);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            System.out.println("[EmailService] Excel import credentials email sent successfully to " + toEmail);
        } catch (Exception e) {
            System.err.println("[EmailService] Failed to send Excel import credentials email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildExcelImportEmailHtml(String firstName, String username, String password) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:Inter,Arial,sans-serif;background:#f4f6fb;margin:0;padding:0;}"
                + ".wrap{max-width:540px;margin:40px auto;background:white;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.10);}"
                + ".header{background:linear-gradient(135deg,#6366f1,#ec4899);padding:36px 40px;text-align:center;}"
                + ".header h1{color:white;margin:0;font-size:24px;font-weight:800;letter-spacing:-0.5px;}"
                + ".header p{color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;}"
                + ".body{padding:36px 40px; color:#1f2937; font-size:14px; line-height:1.6;}"
                + ".cred-box{background:#f8fafc; border:1px solid #e2e8f0; border-radius:12px; padding:20px; margin:20px 0;}"
                + ".cred-item{margin-bottom:10px; font-family:monospace; font-size:16px;}"
                + ".cred-label{font-weight:bold; display:inline-block; width:150px; font-family:sans-serif; font-size:14px; color:#4b5563;}"
                + ".btn{display:inline-block;background:linear-gradient(135deg,#6366f1,#4f46e5);color:white;text-align:center;padding:12px 24px;border-radius:10px;text-decoration:none;font-weight:700;font-size:15px;margin:20px 0;}"
                + ".footer{margin-top:30px; border-top:1px solid #f0f0f0; padding-top:20px; font-size:13px; color:#6b7280;}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='header'>"
                + "<h1>🏢 HR Portal</h1>"
                + "<p>Your system account is ready</p>"
                + "</div>"
                + "<div class='body'>"
                + "Dear " + firstName + ",<br/><br/>"
                + "Welcome to the company.<br/><br/>"
                + "Your system account has been successfully created.<br/><br/>"
                + "<strong>Login Details:</strong>"
                + "<div class='cred-box'>"
                + "<div class='cred-item'><span class='cred-label'>Username:</span> " + username + "</div>"
                + "<div class='cred-item'><span class='cred-label'>Temporary Password:</span> " + password + "</div>"
                + "</div>"
                + "Please log in and change your password after your first login.<br/><br/>"
                + "<a href='http://localhost:5173/login' class='btn'>Log In to System</a>"
                + "<div class='footer'>"
                + "Best Regards,<br/>"
                + "HR / Administration Team"
                + "</div>"
                + "</div>"
                + "</div></body></html>";
    }

    /**
     * Sends a birthday wishing email to the employee.
     */
    @Async
    public void sendBirthdayWishEmail(String toEmail, String fullName, String profilePhotoUrl) {
        if (!isValidEmail(toEmail)) {
            System.err.println("[EmailService] Skipping birthday email - invalid/placeholder address: " + toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, "D Tech HRIS"));
            helper.setTo(toEmail);
            helper.setReplyTo(fromEmail);
            helper.setSubject("Happy Birthday, " + fullName + "!");
            message.setHeader("X-Mailer", "D Tech HRIS Mailer");

            // Always use the public ui-avatars service so the image works in external email clients.
            // Local server URLs (http://localhost:8080/...) are not reachable by recipient mail clients.
            String imgUrl = "https://ui-avatars.com/api/?name="
                    + java.net.URLEncoder.encode(fullName, java.nio.charset.StandardCharsets.UTF_8)
                    + "&size=120&background=f43f5e&color=fff";

            String htmlBody = buildBirthdayEmailHtml(fullName, imgUrl);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            System.out.println("[EmailService] Birthday email sent successfully to " + toEmail);
        } catch (Exception e) {
            System.err.println("[EmailService] Failed to send birthday email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildBirthdayEmailHtml(String name, String imgUrl) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:Inter,Arial,sans-serif;background:#fff5f7;margin:0;padding:0;}"
                + ".wrap{max-width:520px;margin:40px auto;background:white;border-radius:24px;overflow:hidden;box-shadow:0 12px 40px rgba(244,63,94,0.15);border:1px solid #ffe4e6;}"
                + ".header{background:linear-gradient(135deg,#f43f5e,#ec4899);padding:40px;text-align:center;color:white;}"
                + ".header h1{margin:0;font-size:28px;font-weight:900;letter-spacing:-0.5px;text-shadow:0 2px 4px rgba(0,0,0,0.1);}"
                + ".header p{margin:8px 0 0;font-size:15px;color:rgba(255,255,255,0.9);}"
                + ".body{padding:40px;text-align:center;color:#3f3f46;}"
                + ".avatar-wrap{margin:10px auto 24px;width:120px;height:120px;border-radius:50%;background:white;padding:6px;box-shadow:0 8px 24px rgba(244,63,94,0.2);}"
                + ".avatar{width:100%;height:100%;border-radius:50%;object-fit:cover;}"
                + ".wishes{font-size:16px;line-height:1.7;margin-bottom:30px;color:#71717a;}"
                + ".accent{font-size:22px;font-weight:800;color:#f43f5e;margin-bottom:12px;}"
                + ".footer{background:#fff1f2;padding:20px;font-size:13px;color:#fda4af;text-align:center;border-top:1px solid #ffe4e6;}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='header'>"
                + "<h1>Happy Birthday! 🎂</h1>"
                + "<p>Wishing you a wonderful day of celebration</p>"
                + "</div>"
                + "<div class='body'>"
                + "<div class='avatar-wrap'><img src='" + imgUrl + "' class='avatar' alt='Profile Photo'/></div>"
                + "<div class='accent'>Warmest Wishes, " + name + "! 🎉</div>"
                + "<div class='wishes'>On behalf of the entire company, we wish you a fantastic birthday filled with joy, success, and happiness. Thank you for being an invaluable part of our team!</div>"
                + "</div>"
                + "<div class='footer'>"
                + "🏢 Sent with 💖 from D Tech HRIS Portal"
                + "</div>"
                + "</div></body></html>";
    }

    /**
     * Sends a broadcast announcement email to the employee.
     */
    @Async
    public void sendBroadcastEmail(String toEmail, String type, String subject, String messageText) {
        if (!isValidEmail(toEmail)) {
            System.err.println("[EmailService] Skipping broadcast email - invalid/placeholder address: " + toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(fromEmail, "D Tech HRIS"));
            helper.setTo(toEmail);
            helper.setReplyTo(fromEmail);
            helper.setSubject(subject);
            message.setHeader("X-Mailer", "D Tech HRIS Mailer");
            message.setHeader("Precedence", "bulk");

            String htmlBody = buildBroadcastEmailHtml(type, subject, messageText);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            System.out.println("[EmailService] Broadcast email sent successfully to " + toEmail);
        } catch (Exception e) {
            System.err.println("[EmailService] Failed to send broadcast email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String buildBroadcastEmailHtml(String type, String subject, String messageText) {
        String primaryColor = "#6366f1";
        String secondaryColor = "#ec4899";
        String headerEmoji = "📢";

        if ("CHRISTMAS".equalsIgnoreCase(type)) {
            primaryColor = "#22c55e"; // Green
            secondaryColor = "#ef4444"; // Red
            headerEmoji = "🎄🎅";
        } else if ("VESAK".equalsIgnoreCase(type)) {
            primaryColor = "#eab308"; // Yellow
            secondaryColor = "#f97316"; // Orange
            headerEmoji = "🏮🙏";
        } else if ("POSON".equalsIgnoreCase(type)) {
            primaryColor = "#06b6d4"; // Cyan
            secondaryColor = "#3b82f6"; // Blue
            headerEmoji = "🌸☸️";
        } else if ("FUNERAL".equalsIgnoreCase(type)) {
            primaryColor = "#374151"; // Grey
            secondaryColor = "#1f2937"; // Dark Grey
            headerEmoji = "🖤🕊️";
        }

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:Inter,Arial,sans-serif;background:#f4f6fb;margin:0;padding:0;}"
                + ".wrap{max-width:560px;margin:40px auto;background:white;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.10);}"
                + ".header{background:linear-gradient(135deg," + primaryColor + "," + secondaryColor + ");padding:36px 40px;text-align:center;}"
                + ".header h1{color:white;margin:0;font-size:24px;font-weight:800;letter-spacing:-0.5px;}"
                + ".header p{color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;}"
                + ".body{padding:36px 40px; color:#1f2937; font-size:15px; line-height:1.6;}"
                + ".msg-card{background:#f8fafc; border-left:4px solid " + primaryColor + "; border-radius:8px; padding:20px; margin:20px 0; font-style:italic;}"
                + ".footer{margin-top:30px; border-top:1px solid #f0f0f0; padding-top:20px; font-size:13px; color:#6b7280; text-align:center;}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='header'>"
                + "<h1>" + headerEmoji + " " + subject + "</h1>"
                + "</div>"
                + "<div class='body'>"
                + "<div class='msg-card'>"
                + messageText.replace("\n", "<br/>")
                + "</div>"
                + "<div class='footer'>"
                + "Sent by Administration Team<br/>"
                + "🏢 D Tech HRIS Portal"
                + "</div>"
                + "</div>"
                + "</div></body></html>";
    }
}
