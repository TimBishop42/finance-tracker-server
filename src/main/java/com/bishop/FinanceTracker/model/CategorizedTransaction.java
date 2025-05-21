package com.bishop.FinanceTracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class CategorizedTransaction {
    @JsonProperty("transaction_id")
    private int transactionId;
    private Date date;
    private float amount;
    @JsonProperty("business_name")
    private String businessName;
    private String comment;
    @JsonProperty("predicted_category")
    private String predictedCategory;
    @JsonProperty("confidence_score")
    private float confidenceScore;

    // Getters and Setters
    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public void setPredictedCategory(String predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public float getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(float confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
} 