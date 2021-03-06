package io.nuls.api.rpc.controller;

import io.nuls.api.ApiContext;
import io.nuls.api.analysis.WalletRpcHandler;
import io.nuls.api.cache.ApiCache;
import io.nuls.api.constant.AddressType;
import io.nuls.api.db.*;
import io.nuls.api.exception.JsonRpcException;
import io.nuls.api.manager.CacheManager;
import io.nuls.api.model.po.*;
import io.nuls.api.model.rpc.RpcErrorCode;
import io.nuls.api.model.rpc.RpcResult;
import io.nuls.api.model.rpc.RpcResultError;
import io.nuls.api.model.rpc.SearchResultDTO;
import io.nuls.api.service.SymbolUsdtPriceProviderService;
import io.nuls.api.utils.AssetTool;
import io.nuls.api.utils.DBUtil;
import io.nuls.api.utils.VerifyUtils;
import io.nuls.base.basic.AddressTool;
import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Controller;
import io.nuls.core.core.annotation.RpcMethod;
import io.nuls.core.parse.MapUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ChainController {

    @Autowired
    private BlockService blockService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private ContractService contractService;
    @Autowired
    private StatisticalService statisticalService;
    @Autowired
    private AgentService agentService;

    @Autowired
    SymbolQuotationPriceService symbolPriceService;

    @Autowired
    BlockTimeService blockTimeService;

    @Autowired
    SymbolRegService symbolRegService;

    @Autowired
    SymbolUsdtPriceProviderService symbolUsdtPriceProviderService;

    @RpcMethod("getChainInfo")
    public RpcResult getChainInfo(List<Object> params) {
        return RpcResult.success(CacheManager.getCache(ApiContext.defaultChainId).getChainInfo());
    }

    @RpcMethod("getOtherChainList")
    public RpcResult getOtherChainList(List<Object> params) {
        VerifyUtils.verifyParams(params, 1);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }

        List<Map<String, Object>> chainInfoList = new ArrayList<>();
        for (ChainInfo chainInfo : CacheManager.getChainInfoMap().values()) {
            if (chainInfo.getChainId() != chainId) {
                Map<String, Object> map = new HashMap<>();
                map.put("chainId", chainInfo.getChainId());
                map.put("chainName", chainInfo.getChainName());
                chainInfoList.add(map);
            }
        }
        return RpcResult.success(chainInfoList);

    }

    @RpcMethod("getInfo")
    public RpcResult getInfo(List<Object> params) {
        VerifyUtils.verifyParams(params, 1);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!CacheManager.isChainExist(chainId)) {
            return RpcResult.dataNotFound();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("chainId", chainId);
        map.put("networkHeight", ApiContext.networkHeight);
        map.put("localHeight", ApiContext.localHeight);

        ApiCache apiCache = CacheManager.getCache(chainId);
        AssetInfo assetInfo = apiCache.getChainInfo().getDefaultAsset();
        Map<String, Object> assetMap = new HashMap<>();
        assetMap.put("chainId", assetInfo.getChainId());
        assetMap.put("assetId", assetInfo.getAssetId());
        assetMap.put("symbol", assetInfo.getSymbol());
        assetMap.put("decimals", assetInfo.getDecimals());
        map.put("defaultAsset", assetMap);
        //agentAsset
        assetInfo = CacheManager.getRegisteredAsset(DBUtil.getAssetKey(apiCache.getConfigInfo().getAgentChainId(), apiCache.getConfigInfo().getAgentAssetId()));
        if (assetInfo != null) {
            assetMap = new HashMap<>();
            assetMap.put("chainId", assetInfo.getChainId());
            assetMap.put("assetId", assetInfo.getAssetId());
            assetMap.put("symbol", assetInfo.getSymbol());
            assetMap.put("decimals", assetInfo.getDecimals());
            map.put("agentAsset", assetMap);
        } else {
            map.put("agentAsset", null);
        }
        map.put("magicNumber", ApiContext.magicNumber);
        map.put("isRunCrossChain", ApiContext.isRunCrossChain);
        map.put("isRunSmartContract", ApiContext.isRunSmartContract);
        return RpcResult.success(map);
    }

    @RpcMethod("getCoinInfo")
    public RpcResult getCoinInfo(List<Object> params) {
        VerifyUtils.verifyParams(params, 1);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!CacheManager.isChainExist(chainId)) {
            return RpcResult.dataNotFound();
        }
        ApiCache apiCache = CacheManager.getCache(chainId);
        return RpcResult.success(apiCache.getCoinContextInfo());
    }

    @RpcMethod("search")
    public RpcResult search(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);

        int chainId;
        String text;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        try {
            text = params.get(1).toString().trim();
        } catch (Exception e) {
            return RpcResult.paramError("[text] is invalid");
        }

        if (!CacheManager.isChainExist(chainId)) {
            return RpcResult.dataNotFound();
        }
        int length = text.length();
        SearchResultDTO result = null;
        if (length < 20) {
            result = getBlockByHeight(chainId, text);
        } else if (length < 40) {
            boolean isAddress = AddressTool.validAddress(chainId, text);
            if (isAddress) {
                byte[] address = AddressTool.getAddress(text);
                if (address[2] == AddressType.CONTRACT_ADDRESS_TYPE) {
                    result = getContractByAddress(chainId, text);
                } else {
                    result = getAccountByAddress(chainId, text);
                }
            }
        } else {
            result = getResultByHash(chainId, text);
        }
        if (null == result) {
            return RpcResult.dataNotFound();
        }
        return new RpcResult().setResult(result);
    }

    private SearchResultDTO getContractByAddress(int chainId, String text) {
        ContractInfo contractInfo;
        contractInfo = contractService.getContractInfo(chainId, text);
        SearchResultDTO dto = new SearchResultDTO();
        dto.setData(contractInfo);
        dto.setType("contract");
        return dto;
    }

    private SearchResultDTO getResultByHash(int chainId, String hash) {

        BlockHeaderInfo blockHeaderInfo = blockService.getBlockHeaderByHash(chainId, hash);
        if (blockHeaderInfo != null) {
            return getBlockInfo(chainId, blockHeaderInfo);
        }

        Result<TransactionInfo> result = WalletRpcHandler.getTx(chainId, hash);
        if (result == null) {
            throw new JsonRpcException(new RpcResultError(RpcErrorCode.DATA_NOT_EXISTS));
        }
        if (result.isFailed()) {
            throw new JsonRpcException(result.getErrorCode());
        }
        TransactionInfo tx = result.getData();
        SearchResultDTO dto = new SearchResultDTO();
        dto.setData(tx);
        dto.setType("tx");
        return dto;
    }

    private SearchResultDTO getAccountByAddress(int chainId, String address) {
        if (!AddressTool.validAddress(chainId, address)) {
            throw new JsonRpcException(new RpcResultError(RpcErrorCode.PARAMS_ERROR, "[address] is inValid"));
        }

        AccountInfo accountInfo = accountService.getAccountInfo(chainId, address);
        if (accountInfo == null) {
            accountInfo = new AccountInfo(address);

        }
        SearchResultDTO dto = new SearchResultDTO();
        dto.setData(accountInfo);
        dto.setType("account");
        return dto;
    }

    private SearchResultDTO getBlockByHeight(int chainId, String text) {
        Long height;
        try {
            height = Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
        BlockHeaderInfo blockHeaderInfo = blockService.getBlockHeader(chainId, height);
        if (blockHeaderInfo == null) {
            return null;
        }
        return getBlockInfo(chainId, blockHeaderInfo);
    }

    private SearchResultDTO getBlockInfo(int chainId, BlockHeaderInfo blockHeaderInfo) {
        Result<BlockInfo> result = WalletRpcHandler.getBlockInfo(chainId, blockHeaderInfo.getHash());
        if (result.isFailed()) {
            throw new JsonRpcException(result.getErrorCode());
        }
        BlockInfo block = result.getData();
        if (null == block) {
            return null;
        } else {
            SearchResultDTO dto = new SearchResultDTO();
            dto.setData(block);
            dto.setType("block");
            return dto;
        }
    }

    @RpcMethod("getByzantineCount")
    public RpcResult getByzantineCount(List<Object> params) {
        int chainId;
        String txHash;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            txHash = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[txHash] is inValid");
        }

        Result result = WalletRpcHandler.getByzantineCount(chainId, txHash);
        if (result.isFailed()) {
            throw new JsonRpcException(result.getErrorCode());
        }
        Map<String, Object> map = (Map<String, Object>) result.getData();
        return RpcResult.success(map);
    }

    @RpcMethod("assetGet")
    public RpcResult assetGet(List<Object> params) {
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        ApiCache apiCache = CacheManager.getCache(chainId);
        if (apiCache == null) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        CoinContextInfo coinContextInfo = apiCache.getCoinContextInfo();
        Map<String, Object> map = new HashMap<>();
//        nvtInflationAmount": 1,   //nvt当前通胀量  通过stacking快照表获取
        BigInteger nvtInflationAmount = coinContextInfo.getRewardTotal();
        map.put("nvtInflationAmount", nvtInflationAmount);
//        nvtInflationTotal": 1,    //通胀总量      通过共识模块配置获取
        map.put("nvtInflationTotal", ApiContext.totalInflationAmount);
//        nvtInitialAmount": 1,     //初始发行量    通过区块模块配置文件获
        BigInteger nvtInitialAmount = ApiContext.initialAmount;
        map.put("nvtInitialAmount", nvtInitialAmount);
//        nvtPublishAmount": 1,     //当前发行量    当前通胀量 + 初始发行量
        BigInteger nvtPublishAmount = nvtInflationAmount.add(nvtInitialAmount);
        map.put("nvtPublishAmount", nvtPublishAmount);
//        nvtDepositAmount": 1,      //当前抵押量

//          nvtLockedAmount          //当期锁定量   查询几个固定的锁定资产
        BigInteger nvtLockedAmount = coinContextInfo.getBusiness().add(coinContextInfo.getCommunity()).add(coinContextInfo.getTeam()).add(coinContextInfo.getDestroy());
        map.put("nvtLockedAmount", nvtLockedAmount);
//        nvtTurnoverAmount": 1,     //当前流通量   当前发行量 - 当期锁定量
        map.put("nvtTurnoverAmount", nvtPublishAmount.subtract(nvtLockedAmount));
//        nvtTotal": 1,              //nvt总量     初始发行量 + 通胀总量
        map.put("nvtTotal", ApiContext.totalInflationAmount.add(nvtInitialAmount));
//        nvtUsdValue": 1,
        SymbolPrice symbolPrice = symbolPriceService.getFreshUsdtPrice(ApiContext.defaultSymbol);
        BigDecimal nvtUsdtValue = symbolPrice.getPrice().multiply(new BigDecimal(nvtPublishAmount).movePointLeft(ApiContext.defaultDecimals));
        map.put("nvtUsdtValue", nvtUsdtValue.movePointRight(ApiContext.defaultDecimals).toBigInteger());
//        crossChainAssetValue": 1
        return RpcResult.success(map);
    }


    @RpcMethod("getNodeInfo")
    public RpcResult getNodeInfo(List<Object> params) {
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
//                "nodeCount": 1,                //普通节点数量          共识模块RPC接口实时获取
//                "bankNodeCount": 1,            //银行节点数量          虚拟银行模块RPC接口实时获取
//                "blockHeight": 1,              //区块高度              BlockTimeInfo
//                "avgBlockTimeConsuming": 1,    //平均出块耗时           BlockTimeInfo
//                "lastBlockTimeConsuming": 1    //最后一个块出块耗时       BlockTimeInfo
        Map<String, Object> map = new HashMap<>();
        long count = 0;
        ApiCache apiCache = CacheManager.getCache(chainId);
        if (apiCache.getBestHeader() != null) {
            count = agentService.agentsCount(chainId, apiCache.getBestHeader().getHeight());
        }
        map.put("nodeCount", count);
        map.put("bankNodeCount", 0L);
        BlockTimeInfo blockTimeInfo = blockTimeService.get(chainId);
        map.put("blockHeight", blockTimeInfo.getBlockHeight());
        map.put("avgBlockTimeConsuming", blockTimeInfo.getAvgConsumeTime());
        map.put("lastBlockTimeConsuming", blockTimeInfo.getLastConsumeTime());
        return RpcResult.success(map);
    }

    /**
     * 获取所有币种的基础信息
     *
     * @return
     */
    @RpcMethod("getSymbolBaseInfo")
    public RpcResult getSymbolBaseInfo(List<Object> params) {
        List<SymbolRegInfo> symbolList = symbolRegService.getAll();
        SymbolPrice usd = symbolUsdtPriceProviderService.getSymbolPriceForUsdt("USD");
        SymbolRegInfo usdInfo = symbolRegService.get(0,0);
        return RpcResult.success(
                symbolList.stream().map(d -> {
                    Map<String, Object> map = MapUtils.beanToMap(d);
                    SymbolPrice price = symbolUsdtPriceProviderService.getSymbolPriceForUsdt(d.getSymbol());
                    map.put("usdPrice", usd.transfer(price, BigDecimal.ONE).setScale(usdInfo.getDecimals(), RoundingMode.HALF_DOWN));
                    return map;
                }).collect(Collectors.toList()));
    }

    /**
     * 获取所有币种的基础信息
     *
     * @return
     */
    @RpcMethod("getSymbolInfo")
    public RpcResult getSymbolInfo(List<Object> params) {
        int chainId, assetId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            assetId = (int) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[assetId] is inValid");
        }
        SymbolRegInfo symbolRegInfo = symbolRegService.get(chainId, assetId);
        if (symbolRegInfo == null) {
            return RpcResult.dataNotFound();
        }
        SymbolPrice usd = symbolUsdtPriceProviderService.getSymbolPriceForUsdt("USD");
        SymbolRegInfo usdInfo = symbolRegService.get(0, 0);
        Map<String, Object> map = MapUtils.beanToMap(symbolRegInfo);
        SymbolPrice price = symbolUsdtPriceProviderService.getSymbolPriceForUsdt(symbolRegInfo.getSymbol());
        map.put("usdPrice", usd.transfer(price, BigDecimal.ONE).setScale(usdInfo.getDecimals(), RoundingMode.HALF_DOWN));
        return RpcResult.success(map);
    }


//    @RpcMethod("assetGet")
//    public RpcResult assetGet(List<Object> params) {
//        int chainId;
//        try {
//            chainId = (int) params.get(0);
//        } catch (Exception e) {
//            return RpcResult.paramError("[chainId] is inValid");
//        }
//
//        ApiCache apiCache = CacheManager.getCache(chainId);
//        if (apiCache == null) {
//            return RpcResult.paramError("[chainId] is inValid");
//        }
//        CoinContextInfo coinContextInfo = apiCache.getCoinContextInfo();
//        Map<String, Object> map = new HashMap<>();
//        map.put("trades", coinContextInfo.getTxCount());
//        map.put("totalAssets", AssetTool.toDouble(coinContextInfo.getTotal()));
//        map.put("circulation", AssetTool.toDouble(coinContextInfo.getCirculation()));
//        map.put("deposit", AssetTool.toDouble(coinContextInfo.getConsensusTotal()));
//        map.put("business", AssetTool.toDouble(coinContextInfo.getBusiness()));
//        map.put("team", AssetTool.toDouble(coinContextInfo.getTeam()));
//        map.put("community", AssetTool.toDouble(coinContextInfo.getCommunity()));
//        int consensusCount = apiCache.getCurrentRound().getMemberCount() - apiCache.getChainInfo().getSeeds().size();
//        if (consensusCount < 0) {
//            consensusCount = 0;
//        }
//        map.put("consensusNodes", consensusCount);
//        long count = 0;
//        if (apiCache.getBestHeader() != null) {
//            count = agentService.agentsCount(chainId, apiCache.getBestHeader().getHeight());
//        }
//        map.put("totalNodes", count);
//        return RpcResult.success(map);
//    }

    @RpcMethod("getTotalSupply")
    public RpcResult getTotalSupply(List<Object> params) {
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }

        ApiCache apiCache = CacheManager.getCache(chainId);
        if (apiCache == null) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        CoinContextInfo coinContextInfo = apiCache.getCoinContextInfo();
        Map<String, Object> map = new HashMap<>();
        BigInteger supply = coinContextInfo.getTotal().subtract(coinContextInfo.getDestroy());
        map.put("supplyCoin", AssetTool.toCoinString(supply) + "");
        return RpcResult.success(map);
    }


    @RpcMethod("getCirculation")
    public RpcResult getCirculation(List<Object> params) {
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }

        ApiCache apiCache = CacheManager.getCache(chainId);
        if (apiCache == null) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        CoinContextInfo coinContextInfo = apiCache.getCoinContextInfo();
        Map<String, Object> map = new HashMap<>();
        map.put("circulation", AssetTool.toCoinString(coinContextInfo.getCirculation()) + "");
        return RpcResult.success(map);
    }

    @RpcMethod("getDestroy")
    public RpcResult getDestroy(List<Object> params) {
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }

        ApiCache apiCache = CacheManager.getCache(chainId);
        if (apiCache == null) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        CoinContextInfo coinContextInfo = apiCache.getCoinContextInfo();
        Map<String, Object> map = new HashMap<>();
        map.put("destroy", AssetTool.toCoinString(coinContextInfo.getDestroy()) + "");
        map.put("list", coinContextInfo.getDestroyInfoList());
        return RpcResult.success(map);
    }

    /**
     * 币种统计数据
     *
     * @return
     */
    @RpcMethod("symbolReport")
    public RpcResult symbolReport(List<Object> params) {
        VerifyUtils.verifyParams(params, 1);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        return RpcResult.success(statisticalService.getAssetSnapshotAggSum(chainId,4).stream().map(d->{
            SymbolRegInfo asset = symbolRegService.get(d.getAssetChainId(),d.getAssetId());
            return Map.of(
                    "symbol",d.getSymbol(),
                    "total",d.getTotal(),
                    "convert24",d.getConverterInTotal(),
                    "redeem24",d.getConverterOutTotal(),
                    "transfer24",d.getTxTotal(),
                    "addressCount",d.getAddressCount(),
                    "icon",asset.getIcon(),
                    "assetChainId",d.getAssetChainId(),
                    "assetId",d.getAssetId()
            );
        }).collect(Collectors.toList()));
    }

}
