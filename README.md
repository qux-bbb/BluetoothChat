# BluetoothChat
#### 蓝牙聊天app  

基于google官方蓝牙聊天示例app编写  
没有用fragment  
去掉了log显示  
自定义标题栏，添加了连接状态  

#### Bug
有时候会出现一些bug，但因为这些bug有时候出现，有时候又没有了，莫名其妙的，所以不知道怎么调，大神来帮忙啊  
* a连接b之后，a断开连接再连接b，无法连接
* 发起连接的一方看不到对方发送的消息
* 发起连接的一方在对方断开连接之后标题栏状态没有更新  

※ 在github上搜了下，发现有人已经写出这样的app了，地址：https://github.com/cuihee/DAY7_BluetoothSerial.git  
  发现了另外一个  地址：https://github.com/songbangyan/Bluetooth2Uart.git
  抽时间对比一下看看我的bug怎么解决
