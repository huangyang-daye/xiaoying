package com.xiaoying.studio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.xiaoying.studio.Models.*;

@Service
public class AccountService implements ApplicationRunner {
    static final String DEMO_PASSWORD = "demo-password";
    private static final String DUMMY_HASH = Passwords.hash("not-a-real-password");
    private final StudioMapper db;
    private final FileStorage files;
    public AccountService(StudioMapper db, FileStorage files) { this.db = db; this.files = files; }
    @Override public void run(ApplicationArguments args) { ensureDemo("demo-a"); ensureDemo("demo-b"); }
    public User ensureDemo(String key) {
        String name = switch (key == null ? "" : key) {
            case "demo-a" -> "演示用户 A";
            case "demo-b" -> "演示用户 B";
            default -> throw ApiException.invalid("demoKey 仅支持 demo-a 或 demo-b");
        };
        Account existing = db.account(key);
        if (existing != null) return view(existing);
        Instant now = Instant.now();
        Account created = new Account();
        created.id = key;
        created.username = key;
        created.email = key + "@studio.local";
        created.passwordHash = Passwords.hash(DEMO_PASSWORD);
        created.displayName = name;
        created.createdAt = now;
        created.updatedAt = now;
        try { db.insertAccount(created); }
        catch (DuplicateKeyException ignored) { /* Another request created the demo account. */ }
        Account stored = db.account(key);
        if (stored == null) throw new ApiException(503, "ACCOUNT_UNAVAILABLE", "演示账户暂不可用");
        return view(stored);
    }
    public User require(String id) {
        Account account = db.account(id);
        if (account == null) throw new ApiException(401, "UNAUTHENTICATED", "请先登录或选择演示身份");
        return view(account);
    }
    public String avatarKey(String id) {
        Account account = db.account(id);
        if (account == null || account.avatarKey == null) throw ApiException.missing("AVATAR");
        return account.avatarKey;
    }
    public User register(RegisterRequest request) {
        String email = email(request == null ? null : request.email());
        String username = username(request == null ? null : request.username());
        String password = password(request == null ? null : request.password(), "密码");
        if (password.equalsIgnoreCase(username) || password.equalsIgnoreCase(email)) throw ApiException.invalid("密码不能与用户名或邮箱相同");
        Instant now = Instant.now();
        Account account = new Account();
        account.id = UUID.randomUUID().toString().replace("-", "");
        account.username = username;
        account.email = email;
        account.passwordHash = Passwords.hash(password);
        account.displayName = nickname(request.displayName(), username);
        account.createdAt = now;
        account.updatedAt = now;
        try { db.insertAccount(account); }
        catch (DuplicateKeyException e) { throw new ApiException(409, "ACCOUNT_EXISTS", "用户名或邮箱已被使用"); }
        return view(account);
    }
    public User login(LoginRequest request) {
        String raw = Models.text(request == null ? null : request.login());
        String password = request == null ? null : request.password();
        Account account = null;
        if (raw != null) {
            String normalized = raw.toLowerCase(Locale.ROOT);
            account = normalized.contains("@") ? db.accountByEmail(normalized) : db.accountByUsername(normalized);
        }
        boolean matched = Passwords.matches(password == null ? "" : password, account == null ? DUMMY_HASH : account.passwordHash);
        if (account == null || password == null || password.isBlank() || !matched) throw new ApiException(401, "INVALID_CREDENTIALS", "用户名或密码不正确");
        return view(account);
    }
    public User rename(String id, String displayName) {
        Account account = stored(id);
        account.displayName = nickname(displayName, null);
        account.updatedAt = Instant.now();
        db.updateProfile(account);
        return view(account);
    }
    public User changePassword(String id, String currentPassword, String newPassword) {
        Account account = stored(id);
        if (currentPassword == null || !Passwords.matches(currentPassword, account.passwordHash)) throw ApiException.invalid("当前密码不正确");
        String updated = password(newPassword, "新密码");
        if (updated.equalsIgnoreCase(account.username) || updated.equalsIgnoreCase(account.email)) throw ApiException.invalid("新密码不能与用户名或邮箱相同");
        if (Passwords.matches(updated, account.passwordHash)) throw ApiException.invalid("新密码不能与当前密码相同");
        account.passwordHash = Passwords.hash(updated);
        account.updatedAt = Instant.now();
        db.updatePassword(account);
        return view(account);
    }
    public User changeAvatar(String id, MultipartFile file) {
        Account account = stored(id);
        String mime = files.inspectAvatar(file);
        String key = "avatars/" + id + (mime.equals("image/png") ? ".png" : ".jpg");
        if (account.avatarKey != null && !account.avatarKey.equals(key)) files.delete(account.avatarKey);
        files.delete(key);
        files.save(file, key);
        account.avatarKey = key;
        account.updatedAt = Instant.now();
        db.updateAvatar(account);
        return view(account);
    }
    @Transactional
    public void delete(String id, String password) {
        Account account = db.lockAccount(id);
        if(account==null) throw new ApiException(401,"UNAUTHENTICATED","账户已失效");
        if (password == null || !Passwords.matches(password, account.passwordHash)) throw ApiException.invalid("密码不正确，账户未删除");
        List<String> removable = new ArrayList<>();
        if (account.avatarKey != null) removable.add(account.avatarKey);
        for (Task task : db.lockOwnedTasks(id)) {
            db.deleteSteps(task.id);
            db.deleteTaskLinks(task.id);
            if (task.resultKey != null) removable.add(task.resultKey);
            db.deleteTask(task.id);
        }
        for (Asset asset : db.assetsByOwner(id)) {
            db.deleteAssetLinks(asset.id);
            removable.add(asset.storageKey);
            db.deleteAsset(asset.id);
        }
        for (Template template : db.templatesByOwner(id)) {
            String sample = template.sampleKey;
            db.deleteTemplate(template.id);
            if (db.templatesUsingSample(sample) == 0) removable.add("samples/" + sample + ".png");
        }
        db.deleteAccount(id);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { removable.forEach(files::delete); }
            });
    }
    private Account stored(String id) {
        Account account = db.account(id);
        if (account == null) throw new ApiException(401, "UNAUTHENTICATED", "请先登录或选择演示身份");
        return account;
    }
    private User view(Account account) {
        return new User(account.id, account.username, account.email, account.displayName, account.avatarKey == null ? null : "/files/avatars/" + account.id);
    }
    private static String email(String raw) {
        String value = Models.text(raw);
        if (value == null || value.length() > 254 || !value.matches("(?i)^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.invalid("请填写有效邮箱");
        return value.toLowerCase(Locale.ROOT);
    }
    private static String username(String raw) {
        String value = Models.text(raw);
        if (value == null) throw ApiException.invalid("请填写用户名");
        value = value.toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9_-]{2,31}")) throw ApiException.invalid("用户名需为 3～32 位字母、数字、下划线或连字符，并以字母或数字开头");
        if (value.equals("demo-a") || value.equals("demo-b")) throw ApiException.invalid("该用户名已保留");
        return value;
    }
    private static String password(String raw, String label) {
        if (raw == null || raw.isBlank()) throw ApiException.invalid("请填写" + label);
        if (raw.length() < 8 || raw.length() > 64) throw ApiException.invalid(label + "需为 8～64 位");
        return raw;
    }
    private static String nickname(String raw, String fallback) {
        String value = Models.text(raw);
        if (value == null) {
            if (fallback != null) return fallback;
            throw ApiException.invalid("请填写昵称");
        }
        if (value.length() > 40) throw ApiException.invalid("昵称不能超过 40 个字");
        return value;
    }
}
