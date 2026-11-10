package com.wayfare.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ResendEmailService {

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:Wayfare <onboarding@resend.dev>}")
    private String resendFromEmail;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ResendEmailService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private String getEffectiveApiKey() {
        if (resendApiKey != null && !resendApiKey.trim().isEmpty()) {
            return resendApiKey.trim();
        }
        String envKey = System.getenv("RESEND_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return "";
    }

    /**
     * Gửi email mã OTP khôi phục mật khẩu qua Resend API
     * @param toEmail Email người nhận
     * @param otpCode Mã OTP 6 chữ số
     * @param userName Tên người dùng (nếu có)
     * @return true nếu gửi thành công qua Resend, false nếu có lỗi hoặc chưa cấu hình API key
     */
    public boolean sendPasswordResetOtp(String toEmail, String otpCode, String userName) {
        String apiKey = getEffectiveApiKey();
        if (apiKey.isEmpty()) {
            log.warn("RESEND_API_KEY chưa được cấu hình. Sẽ kích hoạt chế độ fallback mã OTP thông minh cho {}", toEmail);
            return false;
        }

        log.info("Bắt đầu gửi email OTP khôi phục mật khẩu tới: {}", toEmail);
        try {
            String greeting = (userName != null && !userName.trim().isEmpty())
                    ? "Xin chào <strong>" + userName + "</strong>,"
                    : "Xin chào bạn,";

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F8F5EE; margin: 0; padding: 24px; color: #1E1A15; }
                        .container { max-width: 540px; margin: 0 auto; background: #FFFFFF; border-radius: 12px; border: 1px solid rgba(30, 26, 21, 0.1); overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,0.04); }
                        .header { background: #E05836; padding: 28px 24px; text-align: center; }
                        .header h1 { margin: 0; color: #FFFFFF; font-size: 24px; font-weight: 800; letter-spacing: 0.5px; }
                        .header p { margin: 6px 0 0; color: rgba(255, 255, 255, 0.85); font-size: 13px; }
                        .content { padding: 32px 28px; line-height: 1.6; }
                        .otp-box { background: #FFF8F5; border: 2px dashed #E05836; border-radius: 8px; padding: 20px; text-align: center; margin: 24px 0; }
                        .otp-code { font-size: 32px; font-weight: 900; letter-spacing: 8px; color: #E05836; font-family: 'Courier New', Courier, monospace; }
                        .otp-note { font-size: 12px; color: #7A6F60; margin-top: 8px; }
                        .security-tip { background: #F1F5F9; border-left: 4px solid #64748B; padding: 12px 16px; border-radius: 4px; font-size: 12px; color: #475569; margin-top: 20px; }
                        .footer { padding: 18px 28px; background: #FAF7F2; text-align: center; font-size: 11px; color: #9A8F7F; border-top: 1px solid rgba(30, 26, 21, 0.06); }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Wayfare</h1>
                            <p>Hệ thống Khám phá & Chia sẻ Lịch trình Du lịch Thông minh</p>
                        </div>
                        <div class="content">
                            <p>%s</p>
                            <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Wayfare của bạn. Vui lòng sử dụng mã xác thực (OTP) dưới đây để tiếp tục:</p>
                            
                            <div class="otp-box">
                                <div class="otp-code">%s</div>
                                <div class="otp-note">⏱️ Mã này có hiệu lực trong vòng <strong>5 phút</strong>.</div>
                            </div>

                            <div class="security-tip">
                                <strong>Lưu ý bảo mật:</strong> Tuyệt đối không chia sẻ mã này cho bất kỳ ai. Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.
                            </div>
                        </div>
                        <div class="footer">
                            © 2026 Wayfare Inc. Thư này được gửi tự động, vui lòng không phản hồi trực tiếp.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(greeting, otpCode);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", resendFromEmail);
            payload.put("to", List.of(toEmail));
            payload.put("subject", "[Wayfare] Mã xác thực đặt lại mật khẩu: " + otpCode);
            payload.put("html", htmlContent);

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Gửi email qua Resend thành công! HTTP status: {}, Response: {}", response.statusCode(), response.body());
                return true;
            } else {
                log.warn("Resend trả về mã HTTP {}: {}. Sẽ kích hoạt cơ chế fallback mã OTP trực tiếp.", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Lỗi khi kết nối Resend Email API: {}", e.getMessage(), e);
            return false;
        }
    }
}
