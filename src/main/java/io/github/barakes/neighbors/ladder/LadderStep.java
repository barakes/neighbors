package io.github.barakes.neighbors.ladder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One step on someone's ladder. The text is stored only encrypted. */
@Entity
@Table(name = "ladder_steps")
public class LadderStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "text_encrypted", nullable = false)
    private byte[] textEncrypted;

    @Column(nullable = false)
    private boolean reached;

    protected LadderStep() {
        // for JPA
    }

    public LadderStep(Long accountId, int stepOrder, byte[] textEncrypted) {
        this.accountId = accountId;
        this.stepOrder = stepOrder;
        this.textEncrypted = textEncrypted;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public byte[] getTextEncrypted() {
        return textEncrypted;
    }

    public void setTextEncrypted(byte[] textEncrypted) {
        this.textEncrypted = textEncrypted;
    }

    public boolean isReached() {
        return reached;
    }

    /** Only ever set, never unset: a hard day doesn't take a step away. */
    public void markReached() {
        this.reached = true;
    }
}
