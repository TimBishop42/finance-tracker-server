package com.bishop.FinanceTracker.model;

import java.util.Date;

public class TransactionRaw {
    private int id;
    private Date transactionDate;
    private float transactionAmount;
    private String transactionBusiness;

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Date transactionDate) {
        this.transactionDate = transactionDate;
    }

    public float getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(float transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getTransactionBusiness() {
        return transactionBusiness;
    }

    public void setTransactionBusiness(String transactionBusiness) {
        this.transactionBusiness = transactionBusiness;
    }
} 