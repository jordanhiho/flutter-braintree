package com.example.flutter_braintree;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.GooglePayment;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.CardNonce;
import com.braintreepayments.api.DataCollector;

import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import com.braintreepayments.api.models.GooglePaymentCardNonce;
import com.braintreepayments.api.models.ThreeDSecureAdditionalInformation;
import com.braintreepayments.api.models.ThreeDSecureRequest;
import com.braintreepayments.api.models.ThreeDSecureLookup;
import com.braintreepayments.api.ThreeDSecure;
import com.braintreepayments.api.interfaces.ThreeDSecureLookupListener;

import java.util.HashMap;

public class FlutterBraintreeCustom extends AppCompatActivity implements PaymentMethodNonceCreatedListener, BraintreeCancelListener, BraintreeErrorListener {
    private BraintreeFragment braintreeFragment;
    String deviceData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);
        try {
            Intent intent = getIntent();
            braintreeFragment = BraintreeFragment.newInstance(this, intent.getStringExtra("authorization"));
            String type = intent.getStringExtra("type");
            if (type.equals("tokenizeCreditCard")) {
                tokenizeCreditCard();
            } else if (type.equals("requestPaypalNonce")) {
                requestPaypalNonce();
            } else if (type.equals("collectDeviceData")) {
                DataCollector.collectDeviceData(braintreeFragment, new BraintreeResponseListener<String>() {
                  @Override
                  public void onResponse(String data) {
                        Intent result = new Intent();

                        HashMap<String, Object> deviceDataMap= new HashMap<String, Object>();
                        deviceDataMap.put("deviceData", data);

                        result.putExtra("type", "collectDeviceData");
                        result.putExtra("deviceData", deviceDataMap);

                        setResult(RESULT_OK, result);
                        finish();
                }
            });
            } else if (type.equals("googlePayPayment")) {
                requestGooglePayPayment();
            } else if (type.equals("isGooglePayReady")) {
                isGooglePayReady();
            } else if (type.equals("threeDSecure")) {
                performThreeDSecureRequest();
            }
            else {
                throw new Exception("Invalid request type: " + type);
            }
        } catch (Exception e) {
            Intent result = new Intent();
            result.putExtra("error", e);
            setResult(2, result);
            finish();
            return;
        }
    }

    protected void tokenizeCreditCard() {
        Intent intent = getIntent();
        collectDeviceData();
        CardBuilder builder = new CardBuilder()
                .cardNumber(intent.getStringExtra("cardNumber"))
                .expirationMonth(intent.getStringExtra("expirationMonth"))
                .expirationYear(intent.getStringExtra("expirationYear"))
                .cvv(intent.getStringExtra("cvv"))
                .validate(false)
                .cardholderName(intent.getStringExtra("cardholderName"));
        Card.tokenize(braintreeFragment, builder);
    }

    protected void requestPaypalNonce() {
        Intent intent = getIntent();
        String paypalIntent;

        collectDeviceData();

        switch (intent.getStringExtra("payPalPaymentIntent")){
            case PayPalRequest.INTENT_ORDER: paypalIntent = PayPalRequest.INTENT_ORDER; break;
            case PayPalRequest.INTENT_SALE: paypalIntent = PayPalRequest.INTENT_SALE; break;
            default: paypalIntent = PayPalRequest.INTENT_AUTHORIZE; break;
        }
        String payPalPaymentUserAction = PayPalRequest.USER_ACTION_DEFAULT;
        if (PayPalRequest.USER_ACTION_COMMIT.equals(intent.getStringExtra("payPalPaymentUserAction"))) {
            payPalPaymentUserAction = PayPalRequest.USER_ACTION_COMMIT;
        }
        PayPalRequest request = new PayPalRequest(intent.getStringExtra("amount"))
                .currencyCode(intent.getStringExtra("currencyCode"))
                .displayName(intent.getStringExtra("displayName"))
                .billingAgreementDescription(intent.getStringExtra("billingAgreementDescription"))
                .intent(paypalIntent)
                .userAction(payPalPaymentUserAction);
        

        if (intent.getStringExtra("amount") == null) {
            // Vault flow
            PayPal.requestBillingAgreement(braintreeFragment, request);
        } else {
            // Checkout flow
            PayPal.requestOneTimePayment(braintreeFragment, request);
        }
    }

    public void collectDeviceData() {
        DataCollector.collectDeviceData(braintreeFragment, new BraintreeResponseListener<String>() {
            @Override
            public void onResponse(String data) {
                deviceData = data;
            }
        });
    }

    protected void requestGooglePayPayment() {
        Intent intent = getIntent();
        collectDeviceData();
        GooglePaymentRequest googlePaymentRequest = new GooglePaymentRequest()
                .transactionInfo(TransactionInfo.newBuilder()
                        .setTotalPrice(intent.getStringExtra("totalPrice"))
                        .setCurrencyCode(intent.getStringExtra("currencyCode"))
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .build())
                .billingAddressRequired(true)
                .googleMerchantId(intent.getStringExtra("googleMerchantID"));

        GooglePayment.requestPayment(braintreeFragment, googlePaymentRequest);
    }

    protected void isGooglePayReady() {
        GooglePayment.isReadyToPay(braintreeFragment, new BraintreeResponseListener<Boolean>() {
            @Override
            public void onResponse(Boolean isReadyToPay) {
                if (isReadyToPay) {
                    // Show Google Pay button
                    Intent result = new Intent();
                    result.putExtra("isReadyToPay", "true");
                    result.putExtra("type", "isReadyToPay");
                    setResult(RESULT_OK, result);
                    finish();
                } else {
                    // Do not show Google Pay button
                    Intent result = new Intent();
                    result.putExtra("isReadyToPay", "false");
                    result.putExtra("type", "isReadyToPay");
                    setResult(RESULT_OK, result);
                    finish();
                }
            }
        });
    }

    protected void performThreeDSecureRequest(){
        Intent intent = getIntent();
        collectDeviceData();
        // eligible for 3DS Verification
        ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
        threeDSecureRequest.amount(intent.getStringExtra("totalPrice"));
//        request.setEmail("test@email.com");
//        request.setBillingAddress(address);
//        request.setAdditionalInformation(additionalInfo);
        threeDSecureRequest.nonce(
                intent.getStringExtra("paymentNonce")
        ); // Use Payment Nonce
        threeDSecureRequest.versionRequested(ThreeDSecureRequest.VERSION_2);

        ThreeDSecure.performVerification(braintreeFragment, threeDSecureRequest, new ThreeDSecureLookupListener() {
            @Override
            public void onLookupComplete(ThreeDSecureRequest request, ThreeDSecureLookup lookup) {
                // Optionally inspect the lookup result and prepare UI if a challenge is required
                ThreeDSecure.continuePerformVerification(braintreeFragment, request, lookup);
            }
        });
    }

    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        nonceMap.put("nonce", paymentMethodNonce.getNonce());
        nonceMap.put("typeLabel", paymentMethodNonce.getTypeLabel());
        nonceMap.put("description", paymentMethodNonce.getDescription());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());
        nonceMap.put("deviceData", deviceData);

        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
            nonceMap.put("paypalPayerId", paypalAccountNonce.getPayerId());
        }
        else if(paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
            nonceMap.put("isNetworkTokenized", cardNonce.isNetworkTokenized());
        }
//        else if(paymentMethodNonce instanceof GooglePaymentCardNonce) {
//            GooglePaymentCardNonce googlePaymentCardNonce = (GooglePaymentCardNonce) paymentMethodNonce;
//            nonceMap.put("isNetworkTokenized", googlePaymentCardNonce.isNetworkTokenized());
//        }
//        else if(paymentMethodNonce instanceof GooglePaymentCardNonce) {
//            GooglePaymentCardNonce googlePaymentCardNonce = (GooglePaymentCardNonce) paymentMethodNonce;
//            nonceMap.put("isNetworkTokenized", googlePaymentCardNonce.isNetworkTokenized());
//        }
        Intent result = new Intent();
        result.putExtra("type", "paymentMethodNonce");
        result.putExtra("paymentMethodNonce", nonceMap);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onCancel(int requestCode) {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onError(Exception error) {
        Intent result = new Intent();
        result.putExtra("error", error);
        setResult(2, result);
        finish();
    }
}
