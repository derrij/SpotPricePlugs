---
layout: default
lang: lv
home: /lv/
blog: /lv/blog/
title: Blogs
description: "Praktiski ceļveži par Nord Pool cenām, viedrozetēm un reālu ietaupījumu."
permalink: /lv/blog/
---

# Blogs

Praktiski ceļveži par Nord Pool cenām, viedrozetēm un reālu ietaupījumu.

{% assign loc = site.posts | where: "lang", "lv" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Vairāk ceļvežu (angliski)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
