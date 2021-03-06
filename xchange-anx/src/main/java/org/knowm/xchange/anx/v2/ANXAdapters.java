package org.knowm.xchange.anx.v2;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.knowm.xchange.anx.v2.dto.account.polling.ANXAccountInfo;
import org.knowm.xchange.anx.v2.dto.account.polling.ANXWallet;
import org.knowm.xchange.anx.v2.dto.marketdata.ANXOrder;
import org.knowm.xchange.anx.v2.dto.marketdata.ANXTicker;
import org.knowm.xchange.anx.v2.dto.marketdata.ANXTrade;
import org.knowm.xchange.anx.v2.dto.meta.ANXMetaData;
import org.knowm.xchange.anx.v2.dto.trade.polling.ANXOpenOrder;
import org.knowm.xchange.anx.v2.dto.trade.polling.ANXTradeResult;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.utils.DateUtils;

/**
 * Various adapters for converting from anx DTOs to XChange DTOs
 */
public final class ANXAdapters {

  private static final String SIDE_BID = "bid";
  private static final int PERCENT_DECIMAL_SHIFT = 2;

  /**
   * private Constructor
   */
  private ANXAdapters() {

  }

  /**
   * Adapts a ANXAccountInfo to an AccountInfo
   *
   * @param anxAccountInfo
   * @return
   */
  public static AccountInfo adaptAccountInfo(ANXAccountInfo anxAccountInfo) {

    // Adapt to XChange DTOs
    AccountInfo accountInfo = new AccountInfo(anxAccountInfo.getLogin(), percentToFactor(anxAccountInfo.getTradeFee()),
        ANXAdapters.adaptWallet(anxAccountInfo.getWallets()));
    return accountInfo;
  }

  public static BigDecimal percentToFactor(BigDecimal percent) {
    return percent.movePointLeft(PERCENT_DECIMAL_SHIFT);
  }

  /**
   * Adapts a ANXOrder to a LimitOrder
   *
   * @param price
   * @param orderTypeString
   * @return
   */
  public static LimitOrder adaptOrder(BigDecimal amount, BigDecimal price, String tradedCurrency, String transactionCurrency, String orderTypeString,
      String id, Date timestamp) {

    // place a limit order
    OrderType orderType = SIDE_BID.equalsIgnoreCase(orderTypeString) ? OrderType.BID : OrderType.ASK;
    CurrencyPair currencyPair = adaptCurrencyPair(tradedCurrency, transactionCurrency);

    LimitOrder limitOrder = new LimitOrder(orderType, amount, currencyPair, id, timestamp, price);

    return limitOrder;

  }

  /**
   * Adapts a List of ANXOrders to a List of LimitOrders
   *
   * @param anxOrders
   * @param currency
   * @param orderType
   * @return
   */
  public static List<LimitOrder> adaptOrders(List<ANXOrder> anxOrders, String tradedCurrency, String currency, String orderType, String id) {

    List<LimitOrder> limitOrders = new ArrayList<LimitOrder>();

    for (ANXOrder anxOrder : anxOrders) {
      limitOrders.add(adaptOrder(anxOrder.getAmount(), anxOrder.getPrice(), tradedCurrency, currency, orderType, id, new Date(anxOrder.getStamp())));
    }

    return limitOrders;
  }

  public static List<LimitOrder> adaptOrders(ANXOpenOrder[] anxOpenOrders) {

    List<LimitOrder> limitOrders = new ArrayList<LimitOrder>();

    for (ANXOpenOrder anxOpenOrder : anxOpenOrders) {
      limitOrders.add(adaptOrder(anxOpenOrder.getAmount().getValue(), anxOpenOrder.getPrice().getValue(), anxOpenOrder.getItem(),
          anxOpenOrder.getCurrency(), anxOpenOrder.getType(), anxOpenOrder.getOid(), new Date(anxOpenOrder.getDate())));
    }

    return limitOrders;
  }

  /**
   * Adapts a ANX Wallet to a XChange Balance
   *
   * @param anxWallet
   * @return
   */
  public static Balance adaptBalance(ANXWallet anxWallet) {

    if (anxWallet == null) { // use the presence of a currency String to indicate existing wallet at ANX
      return null; // an account maybe doesn't contain a ANXWallet
    } else {
      return new Balance(Currency.getInstance(anxWallet.getBalance().getCurrency()), anxWallet.getBalance().getValue(),
          anxWallet.getAvailableBalance().getValue());
    }

  }

  /**
   * Adapts a List of ANX Wallets to an XChange Wallet
   *
   * @param anxWallets
   * @return
   */
  public static Wallet adaptWallet(Map<String, ANXWallet> anxWallets) {

    List<Balance> balances = new ArrayList<Balance>();

    for (ANXWallet anxWallet : anxWallets.values()) {
      Balance balance = adaptBalance(anxWallet);
      if (balance != null) {
        balances.add(balance);
      }
    }
    return new Wallet(balances);

  }

  // public static OrderBookUpdate adaptDepthUpdate(ANXDepthUpdate anxDepthUpdate) {
  //
  // OrderType orderType = anxDepthUpdate.getTradeType().equals("bid") ? OrderType.BID : OrderType.ASK;
  // BigDecimal volume = new BigDecimal(anxDepthUpdate.getVolumeInt()).divide(new BigDecimal(ANXUtils.BTC_VOLUME_AND_AMOUNT_INT_2_DECIMAL_FACTOR));
  //
  // String tradableIdentifier = anxDepthUpdate.getItem();
  // String transactionCurrency = anxDepthUpdate.getCurrency();
  // CurrencyPair currencyPair = new CurrencyPair(tradableIdentifier, transactionCurrency);
  //
  // BigDecimal price = ANXUtils.getPrice(anxDepthUpdate.getPriceInt());
  //
  // BigDecimal totalVolume = new BigDecimal(anxDepthUpdate.getTotalVolumeInt()).divide(new BigDecimal(ANXUtils.BTC_VOLUME_AND_AMOUNT_INT_2_DECIMAL_FACTOR));
  // Date date = new Date(anxDepthUpdate.getNow() / 1000);
  //
  // OrderBookUpdate orderBookUpdate = new OrderBookUpdate(orderType, volume, currencyPair, price, date, totalVolume);
  //
  // return orderBookUpdate;
  // }

  /**
   * Adapts ANXTrade's to a Trades Object
   *
   * @param anxTrades
   * @return
   */
  public static Trades adaptTrades(List<ANXTrade> anxTrades) {

    List<Trade> tradesList = new ArrayList<Trade>();
    long latestTid = 0;
    for (ANXTrade anxTrade : anxTrades) {
      long tid = anxTrade.getTid();
      if (tid > latestTid) {
        latestTid = tid;
      }
      tradesList.add(adaptTrade(anxTrade));
    }
    return new Trades(tradesList, latestTid, TradeSortType.SortByID);
  }

  /**
   * Adapts a ANXTrade to a Trade Object
   *
   * @param anxTrade
   * @return
   */
  public static Trade adaptTrade(ANXTrade anxTrade) {

    OrderType orderType = adaptSide(anxTrade.getTradeType());
    BigDecimal amount = anxTrade.getAmount();
    BigDecimal price = anxTrade.getPrice();
    CurrencyPair currencyPair = adaptCurrencyPair(anxTrade.getItem(), anxTrade.getPriceCurrency());
    Date dateTime = DateUtils.fromMillisUtc(anxTrade.getTid());
    final String tradeId = String.valueOf(anxTrade.getTid());

    return new Trade(orderType, amount, currencyPair, price, dateTime, tradeId);
  }

  public static Ticker adaptTicker(ANXTicker anxTicker) {

    BigDecimal volume = anxTicker.getVol().getValue();
    BigDecimal last = anxTicker.getLast().getValue();
    BigDecimal bid = anxTicker.getBuy().getValue();
    BigDecimal ask = anxTicker.getSell().getValue();
    BigDecimal high = anxTicker.getHigh().getValue();
    BigDecimal low = anxTicker.getLow().getValue();
    Date timestamp = new Date(anxTicker.getNow() / 1000);

    CurrencyPair currencyPair = adaptCurrencyPair(anxTicker.getVol().getCurrency(), anxTicker.getAvg().getCurrency());

    return new Ticker.Builder().currencyPair(currencyPair).last(last).bid(bid).ask(ask).high(high).low(low).volume(volume).timestamp(timestamp)
        .build();

  }

  public static CurrencyPair adaptCurrencyPair(String tradeCurrency, String priceCurrency) {

    return new CurrencyPair(tradeCurrency, priceCurrency);
  }

  public static UserTrades adaptUserTrades(ANXTradeResult[] anxTradeResults, ANXMetaData meta) {

    List<UserTrade> trades = new ArrayList<UserTrade>(anxTradeResults.length);
    for (ANXTradeResult tradeResult : anxTradeResults) {
      trades.add(adaptUserTrade(tradeResult, meta));
    }

    long lastId = trades.size() > 0 ? anxTradeResults[0].getTimestamp().getTime() : 0L;
    return new UserTrades(trades, lastId, TradeSortType.SortByTimestamp);
  }

  private static UserTrade adaptUserTrade(ANXTradeResult aNXTradeResult, ANXMetaData meta) {

    BigDecimal tradedCurrencyFillAmount = aNXTradeResult.getTradedCurrencyFillAmount();
    CurrencyPair currencyPair = adaptCurrencyPair(aNXTradeResult.getCurrencyPair());
    int priceScale = meta.getCurrencyPairs().get(currencyPair).getPriceScale();
    BigDecimal price = aNXTradeResult.getSettlementCurrencyFillAmount().divide(tradedCurrencyFillAmount, priceScale, BigDecimal.ROUND_HALF_EVEN);
    OrderType type = adaptSide(aNXTradeResult.getSide());
    // for fees, getWalletHistory should be used.
    return new UserTrade(type, tradedCurrencyFillAmount, currencyPair, price, aNXTradeResult.getTimestamp(), aNXTradeResult.getTradeId(),
        aNXTradeResult.getOrderId(), null, (Currency) null);
  }

  private static CurrencyPair adaptCurrencyPair(String currencyPairRaw) {

    if ("DOGEBTC".equalsIgnoreCase(currencyPairRaw)) {
      return CurrencyPair.DOGE_BTC;
    } else if (currencyPairRaw.length() != 6) {
      throw new IllegalArgumentException("Unrecognized currency pair " + currencyPairRaw);
    } else {
      return new CurrencyPair(currencyPairRaw.substring(0, 3), currencyPairRaw.substring(3));
    }
  }

  private static OrderType adaptSide(String side) {

    return SIDE_BID.equals(side) ? OrderType.BID : OrderType.ASK;
  }
}
