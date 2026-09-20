package shop.domain;
import jakarta.persistence.*;
@MappedSuperclass
public abstract class Auditable { @Column(name = "created_at") protected java.time.Instant createdAt; public java.time.Instant getCreatedAt() { return createdAt; } }
