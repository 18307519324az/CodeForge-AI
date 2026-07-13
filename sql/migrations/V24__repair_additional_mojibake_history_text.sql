UPDATE generation_message
SET message_content = '历史回复编码损坏'
WHERE message_content REGEXP 'AI 鐢熸垚|鐢熸垚璁板綍|瀵硅瘽鍘嗗彶|搴旂敤璇︽儏|鍐呭缂栫爜寮傚父|寮€鍙戜腑|鑽夌|宸插彂甯|Web 搴旂敤|绠＄悊鍚庡彴|Vue 椤圭洰|鏆傛棤|涓嬭浇|棰勮|杩斿洖|鍔犺浇|澶辫触|鎴愬姛';
