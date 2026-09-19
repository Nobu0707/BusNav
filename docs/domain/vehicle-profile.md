# VehicleProfile domain

`VehicleProfile` は大型バスの物理条件を表す Android/Valhalla 非依存の immutable value である。ID、名称、全長、全幅、全高、車両重量、任意の軸重を持ち、寸法と重量は正値だけを許可する。

Phase 004 は設定画面を持たず、`大型バス（開発用車両条件）` を使用する。仮値は全長12.0m、全幅2.5m、全高3.5m、重量16.0t、軸重10.0tである。実車の車検証・運行条件を表すものではなく、そのまま業務運行に使用してはいけない。

Valhalla adapter がこの値を truck costing の `length`、`width`、`height`、`weight`、`axle_load` へ変換する。domain model 自体は外部 API の option 名を知らない。実車 profile の入力、保存、車両ごとの選択と検証は後続 Phase で実装する。
