import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class VerifyPassword {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String[][] users = {
            {"admin", "Admin@123", "$2a$10$RpmHhWVdt7tAjKPU5VtM1uBHK5d99h9Y5S/KjJPk6PEfYzvwsABni"},
            {"operator", "Operator@123", "$2a$10$mgRq4VDaCzZLjiGVuGSoYO9woY1wQ7hsD0j0d7iANngfV.H41CvPm"},
            {"viewer", "Viewer@123", "$2a$10$k0e1NN/2EbzdADpGh2GecOT0NYhJNy1J7beYFRLESdU2.Qk0VnnFO"}
        };
        for (String[] u : users) {
            boolean match = encoder.matches(u[1], u[2]);
            System.out.println(u[0] + ": " + (match ? "匹配" : "不匹配"));
            if (!match) {
                String newHash = encoder.encode(u[1]);
                System.out.println("  新生成: " + newHash);
                System.out.println("  验证: " + encoder.matches(u[1], newHash));
            }
        }
    }
}
