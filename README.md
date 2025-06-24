# hubitat_SoundSmart_4Zonas
Driver para SoundSmart 4 Zonas TCP/IP e Hubitat




Dentro do Dashboard, no CSS, pode colocar essas configurações para apagar os comandos de Next/Previous do Music Player e deixar só o Mudo.

/* Configuracao para apagar MusicPlayer */ 

.material-icons.music-player.nextTrack 
{display: none;} 
.material-icons.music-player.previousTrack 
{display: none;} 
.trackDescription { visibility: hidden; } 


Exemplo do dashboard. Tem o arquivo aqui no github. 

<img width="1417" alt="image" src="https://github.com/user-attachments/assets/4f4c8efe-4744-4656-b205-b53baad0b421" />

Para uso dele: 
Precisa usar "Buttons" (Botões). 

No caso, o device vai ser a "ZONA", e o numero de botão vai ser o INPUT. 

**Exemplo 1:**
Device: Zone 1
Button: 1 

Vai colocar o Input 1, na Zona 1. 

<img width="1346" alt="image" src="https://github.com/user-attachments/assets/9d5331a9-2013-4d18-bced-96a8590a6797" />
<br>
<br>


**Exemplo 2:**
Device: Zone 2
Button: 4 

Vai colocar o Input 4, na Zona 2.


------
**Para saber o Input que está tocando em uma determinada zona: **
Adicionar um device, e colocar ele do tipo Attribute. Na lista, seleccionar o atributo:  INPUT. 

<img width="1167" alt="image" src="https://github.com/user-attachments/assets/5726b3b0-c9e6-4995-935f-dd9cc013df2b" />


