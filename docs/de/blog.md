---
layout: default
lang: de
home: /de/
blog: /de/blog/
title: Blog
description: "Praktische Leitfäden zu Nord Pool-Preisen, intelligenten Steckdosen und echter Ersparnis."
permalink: /de/blog/
---

# Blog

Praktische Leitfäden zu Nord Pool-Preisen, intelligenten Steckdosen und echter Ersparnis.

{% assign loc = site.posts | where: "lang", "de" %}
{% for post in loc %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }}</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% if page.lang != 'en' %}
<h2>Weitere Leitfäden (auf Englisch)</h2>
{% assign enposts = site.posts | where: "lang", "en" %}
{% for post in enposts %}
<div class="card">
  <a href="{{ site.baseurl }}{{ post.url }}"><strong>{{ post.title }}</strong></a>
  <p class="post-meta">{{ post.date | date: "%Y-%m-%d" }} · EN</p>
  <p>{{ post.excerpt }}</p>
</div>
{% endfor %}
{% endif %}
